package source.transactions.application.externalpayments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.transactions.dto.LightningInvoiceRequestDTO;
import source.transactions.dto.LightningInvoiceResponseDTO;
import source.transactions.exception.ExternalPaymentsExceptions;
import source.transactions.infra.CustodyGateway;
import source.transactions.model.ExternalTransferEntity;
import source.wallet.model.WalletEntity;

import java.time.LocalDateTime;

@Service
public class CreateLightningInvoiceUseCase {

    private final ExternalPaymentsWalletPort walletPort;
    private final ExternalTransfersPort externalTransfersPort;
    private final CustodyGateway custodyGateway;
    private final ExternalPaymentsMath externalPaymentsMath;
    private final ExternalTransferFactory externalTransferFactory;
    private final String localAddressProviderName;

    public CreateLightningInvoiceUseCase(
            ExternalPaymentsWalletPort walletPort,
            ExternalTransfersPort externalTransfersPort,
            CustodyGateway custodyGateway,
            ExternalPaymentsMath externalPaymentsMath,
            ExternalTransferFactory externalTransferFactory,
            @Value("${transactions.local-address-provider-name:KEROSENE_LOCAL}") String localAddressProviderName) {
        this.walletPort = walletPort;
        this.externalTransfersPort = externalTransfersPort;
        this.custodyGateway = custodyGateway;
        this.externalPaymentsMath = externalPaymentsMath;
        this.externalTransferFactory = externalTransferFactory;
        this.localAddressProviderName = localAddressProviderName;
    }

    @Transactional
    public LightningInvoiceResponseDTO create(Long userId, LightningInvoiceRequestDTO request) {
        WalletEntity wallet = walletPort.requireWallet(userId, request.walletName());
        externalPaymentsMath.validatePositiveAmount(request.amount(), "Lightning invoice amount must be positive.");

        long amountSats = externalPaymentsMath.btcToSats(request.amount());
        int expiresInSeconds = request.expiresInSeconds() != null && request.expiresInSeconds() > 0
                ? request.expiresInSeconds()
                : 900;

        CustodyGateway.GeneratedLightningInvoice invoice = custodyGateway.createLightningInvoice(
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

        if (invoice.lightningAddress() != null && !invoice.lightningAddress().isBlank()) {
            wallet.setLightningAddress(invoice.lightningAddress());
            walletPort.save(wallet);
        }

        String provider = resolveProviderName();
        LocalDateTime expiresAt = invoice.expiresAt();
        ExternalTransferEntity transfer = externalTransfersPort.save(externalTransferFactory.newTransfer(
                wallet,
                "LIGHTNING",
                "INBOUND_INVOICE",
                "PENDING",
                provider,
                invoice.lightningAddress(),
                externalPaymentsMath.firstNonBlank(invoice.paymentHash(), invoice.providerReference()),
                invoice.paymentRequest(),
                request.amount(),
                null,
                null,
                null,
                expiresAt,
                request.memo()));

        return new LightningInvoiceResponseDTO(
                transfer.getId(),
                wallet.getName(),
                invoice.paymentRequest(),
                invoice.paymentHash(),
                invoice.lightningAddress(),
                externalPaymentsMath.normalizeBtc(request.amount()),
                provider,
                expiresAt,
                transfer.getStatus());
    }

    private String resolveProviderName() {
        return custodyGateway.providerName() != null ? custodyGateway.providerName() : localAddressProviderName;
    }
}
