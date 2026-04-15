package source.transactions.application.externalpayments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.transactions.dto.ExternalTransferResponseDTO;
import source.transactions.dto.LightningPaymentRequestDTO;
import source.transactions.infra.CustodyGateway;
import source.transactions.model.ExternalTransferEntity;
import source.transactions.service.WalletAuthorizationService;
import source.wallet.model.WalletEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class PayLightningPaymentUseCase {

    private final ExternalPaymentsWalletPort walletPort;
    private final ExternalPaymentsLedgerPort ledgerPort;
    private final ExternalTransfersPort externalTransfersPort;
    private final ExternalPaymentsNotificationPort notificationPort;
    private final WalletAuthorizationService walletAuthorizationService;
    private final CustodyGateway custodyGateway;
    private final ExternalPaymentsFeePolicy feePolicy;
    private final ExternalPaymentsMath externalPaymentsMath;
    private final ExternalTransferFactory externalTransferFactory;
    private final String localAddressProviderName;

    public PayLightningPaymentUseCase(
            ExternalPaymentsWalletPort walletPort,
            ExternalPaymentsLedgerPort ledgerPort,
            ExternalTransfersPort externalTransfersPort,
            ExternalPaymentsNotificationPort notificationPort,
            WalletAuthorizationService walletAuthorizationService,
            CustodyGateway custodyGateway,
            ExternalPaymentsFeePolicy feePolicy,
            ExternalPaymentsMath externalPaymentsMath,
            ExternalTransferFactory externalTransferFactory,
            @Value("${transactions.local-address-provider-name:KEROSENE_LOCAL}") String localAddressProviderName) {
        this.walletPort = walletPort;
        this.ledgerPort = ledgerPort;
        this.externalTransfersPort = externalTransfersPort;
        this.notificationPort = notificationPort;
        this.walletAuthorizationService = walletAuthorizationService;
        this.custodyGateway = custodyGateway;
        this.feePolicy = feePolicy;
        this.externalPaymentsMath = externalPaymentsMath;
        this.externalTransferFactory = externalTransferFactory;
        this.localAddressProviderName = localAddressProviderName;
    }

    @Transactional
    public ExternalTransferResponseDTO pay(Long userId, LightningPaymentRequestDTO request) {
        externalPaymentsMath.validatePositiveAmount(request.amount(), "Lightning payment amount must be positive.");
        if (request.paymentRequest() == null || request.paymentRequest().isBlank()) {
            throw new IllegalArgumentException("A valid Lightning invoice is required.");
        }

        WalletEntity wallet = walletPort.requireWallet(userId, request.fromWalletName());
        WalletAuthorizationService.AuthorizationResult authorization = walletAuthorizationService.authorizeOutboundTransfer(
                userId,
                wallet,
                request.totpCode(),
                request.passkeyAssertionResponseJSON(),
                request.confirmationPassphrase());

        BigDecimal reservedNetworkFee = feePolicy.resolveLightningReservedFee(request.maxRoutingFeeBtc());
        BigDecimal platformFee = feePolicy.calculateWithdrawalFee(userId, request.amount());
        BigDecimal reservedTotal = externalPaymentsMath.normalizeBtc(
                request.amount().add(reservedNetworkFee).add(platformFee));

        ledgerPort.ensureBalance(wallet.getId(), reservedTotal);
        ledgerPort.updateBalance(
                wallet.getId(),
                reservedTotal.negate(),
                "EXTERNAL_LIGHTNING_PAYMENT:" + externalPaymentsMath.safeText(request.description()));

        CustodyGateway.PaymentResult payment = custodyGateway.payLightning(
                new CustodyGateway.LightningPaymentCommand(
                        userId,
                        wallet.getId(),
                        wallet.getName(),
                        request.paymentRequest(),
                        externalPaymentsMath.btcToSats(request.amount()),
                        externalPaymentsMath.btcToSats(reservedNetworkFee),
                        request.description(),
                        authorization.platformSignature()));

        BigDecimal actualFee = payment.feeSats() > 0
                ? externalPaymentsMath.satsToBtc(payment.feeSats())
                : reservedNetworkFee;
        if (actualFee.compareTo(reservedNetworkFee) < 0) {
            BigDecimal feeRefund = externalPaymentsMath.normalizeBtc(reservedNetworkFee.subtract(actualFee));
            ledgerPort.updateBalance(wallet.getId(), feeRefund, "LIGHTNING_NETWORK_FEE_REFUND");
            reservedTotal = reservedTotal.subtract(feeRefund);
        } else if (actualFee.compareTo(reservedNetworkFee) > 0) {
            actualFee = reservedNetworkFee;
        }

        String externalReference = externalPaymentsMath.firstNonBlank(payment.paymentHash(), payment.providerReference());
        ExternalTransferEntity transfer = externalTransfersPort.save(externalTransferFactory.newTransfer(
                wallet,
                "LIGHTNING",
                "OUTBOUND_PAYMENT",
                externalPaymentsMath.firstNonBlank(payment.status(), "SETTLED"),
                resolveProviderName(),
                request.paymentRequest(),
                externalReference,
                request.paymentRequest(),
                request.amount(),
                actualFee,
                platformFee,
                reservedTotal,
                null,
                request.description()));

        ledgerPort.recordPlatformFee(transfer.getId(), userId, reservedTotal, platformFee);
        ledgerPort.recordHistory(new ExternalPaymentsLedgerPort.HistoryRecord(
                userId,
                wallet.getName(),
                externalReference != null ? externalReference : "LIGHTNING",
                "EXTERNAL_LIGHTNING_PAYMENT",
                request.amount(),
                actualFee,
                transfer.getStatus(),
                payment.paymentHash(),
                request.description(),
                LocalDateTime.now()));
        notificationPort.notifyUser(
                userId,
                "Pagamento Lightning enviado",
                "Pagamento Lightning externo encaminhado com sucesso.");

        return externalTransferFactory.toResponseDTO(transfer);
    }

    private String resolveProviderName() {
        return custodyGateway.providerName() != null ? custodyGateway.providerName() : localAddressProviderName;
    }
}
