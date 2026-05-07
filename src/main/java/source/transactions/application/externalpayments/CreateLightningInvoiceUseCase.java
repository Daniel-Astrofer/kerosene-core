package source.transactions.application.externalpayments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.common.idempotency.IdempotencyKeyBuilder;
import source.transactions.dto.LightningInvoiceRequestDTO;
import source.transactions.dto.LightningInvoiceResponseDTO;
import source.transactions.exception.ExternalPaymentsExceptions;
import source.transactions.infra.CustodyGateway;
import source.transactions.infra.LightningInvoiceGateway;
import source.transactions.model.ExternalTransferEntity;
import source.transactions.service.ProcessedTransactionService;
import source.wallet.model.WalletEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class CreateLightningInvoiceUseCase {

    private final ExternalPaymentsWalletPort walletPort;
    private final ExternalTransfersPort externalTransfersPort;
    private final LightningInvoiceGateway lightningInvoiceGateway;
    private final ExternalPaymentsMath externalPaymentsMath;
    private final ExternalTransferFactory externalTransferFactory;
    private final ProcessedTransactionService processedTransactionService;
    private final String localAddressProviderName;

    public CreateLightningInvoiceUseCase(
            ExternalPaymentsWalletPort walletPort,
            ExternalTransfersPort externalTransfersPort,
            @Qualifier("externalLightningInvoiceGateway")
            LightningInvoiceGateway lightningInvoiceGateway,
            ExternalPaymentsMath externalPaymentsMath,
            ExternalTransferFactory externalTransferFactory,
            ProcessedTransactionService processedTransactionService,
            @Value("${transactions.local-address-provider-name:KEROSENE_LOCAL}") String localAddressProviderName) {
        this.walletPort = walletPort;
        this.externalTransfersPort = externalTransfersPort;
        this.lightningInvoiceGateway = lightningInvoiceGateway;
        this.externalPaymentsMath = externalPaymentsMath;
        this.externalTransferFactory = externalTransferFactory;
        this.processedTransactionService = processedTransactionService;
        this.localAddressProviderName = localAddressProviderName;
    }

    @Transactional
    public LightningInvoiceResponseDTO create(Long userId, LightningInvoiceRequestDTO request) {
        requireIdempotencyKey(request.idempotencyKey());
        String idempotencyRef = IdempotencyKeyBuilder.build(
                "external-lightning-invoice",
                String.valueOf(userId),
                request.idempotencyKey());
        AtomicReference<LightningInvoiceResponseDTO> response = new AtomicReference<>();
        boolean processed = processedTransactionService.processOnce(
                idempotencyRef,
                "EXTERNAL_LIGHTNING_INVOICE",
                () -> response.set(createOnce(userId, request, idempotencyRef)));
        if (!processed) {
            return externalTransfersPort.findByIdempotencyKey(idempotencyRef)
                    .map(this::toLightningInvoiceResponse)
                    .orElseThrow(() -> new ExternalPaymentsExceptions.DuplicateExternalPayment(
                            "Lightning invoice already submitted for this idempotency key."));
        }
        return response.get();
    }

    private LightningInvoiceResponseDTO createOnce(Long userId, LightningInvoiceRequestDTO request, String idempotencyRef) {
        WalletEntity wallet = walletPort.requireWallet(userId, request.walletName());
        if (wallet.isSelfCustodyMode()) {
            throw new IllegalStateException(
                    "Self-custody wallets cannot issue custodial Lightning invoices.");
        }
        externalPaymentsMath.validatePositiveAmount(request.amount(), "Lightning invoice amount must be positive.");

        long amountSats = externalPaymentsMath.btcToSats(request.amount());
        int expiresInSeconds = request.expiresInSeconds() != null && request.expiresInSeconds() > 0
                ? request.expiresInSeconds()
                : 900;

        CustodyGateway.GeneratedLightningInvoice invoice = lightningInvoiceGateway.createLightningInvoice(
                new CustodyGateway.LightningInvoiceCommand(
                        userId,
                        wallet.getId(),
                        wallet.getName(),
                        amountSats,
                        request.memo(),
                        expiresInSeconds));

        if (invoice.paymentRequest() == null || invoice.paymentRequest().isBlank()) {
            throw new ExternalPaymentsExceptions.CustodyProviderUnavailable(
                    "The custody provider did not return a Lightning invoice.");
        }
        if (invoice.paymentHash() == null || invoice.paymentHash().isBlank()) {
            throw new ExternalPaymentsExceptions.CustodyProviderUnavailable(
                    "The custody provider did not return a Lightning payment hash.");
        }

        var existingByPaymentHash = externalTransfersPort.findByPaymentHash(invoice.paymentHash());
        if (existingByPaymentHash.isPresent()) {
            ExternalTransferEntity existing = existingByPaymentHash.get();
            if (existing.getUserId() != null
                    && existing.getUserId().equals(userId)
                    && existing.getWalletId() != null
                    && existing.getWalletId().equals(wallet.getId())) {
                return toLightningInvoiceResponse(existing);
            }
            throw new ExternalPaymentsExceptions.DuplicateExternalPayment(
                    "The custody provider returned a duplicate Lightning payment hash.");
        }

        if (invoice.lightningAddress() != null && !invoice.lightningAddress().isBlank()) {
            wallet.setLightningAddress(invoice.lightningAddress());
            walletPort.save(wallet);
        }

        String provider = resolveProviderName();
        LocalDateTime expiresAt = invoice.expiresAt();
        BigDecimal normalizedAmount = externalPaymentsMath.normalizeBtc(request.amount());
        ExternalTransferEntity transfer = externalTransferFactory.newTransfer(
                wallet,
                "LIGHTNING",
                "INBOUND_INVOICE",
                "PENDING",
                provider,
                invoice.lightningAddress(),
                invoice.providerReference(),
                invoice.providerReference(),
                null,
                invoice.paymentHash(),
                invoice.paymentRequest(),
                normalizedAmount,
                null,
                null,
                null,
                expiresAt,
                request.memo());
        transfer.setExpectedAmountBtc(normalizedAmount);
        transfer.setIdempotencyKey(idempotencyRef);
        transfer = externalTransfersPort.save(transfer);

        return toLightningInvoiceResponse(transfer);
    }

    private String resolveProviderName() {
        if (lightningInvoiceGateway.isLive() && lightningInvoiceGateway.providerName() != null) {
            return lightningInvoiceGateway.providerName();
        }
        return localAddressProviderName;
    }

    private LightningInvoiceResponseDTO toLightningInvoiceResponse(ExternalTransferEntity transfer) {
        BigDecimal amount = transfer.getExpectedAmountBtc() != null
                ? transfer.getExpectedAmountBtc()
                : transfer.getAmountBtc();
        return new LightningInvoiceResponseDTO(
                transfer.getId(),
                transfer.getWalletNameSnapshot(),
                transfer.getInvoiceData(),
                transfer.getPaymentHash(),
                transfer.getDestination(),
                externalPaymentsMath.nullableNormalizeBtc(amount),
                transfer.getProvider(),
                transfer.getExpiresAt(),
                transfer.getStatus());
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required for Lightning invoices.");
        }
        if (idempotencyKey.length() > 96) {
            throw new IllegalArgumentException("idempotencyKey must have at most 96 characters.");
        }
    }
}
