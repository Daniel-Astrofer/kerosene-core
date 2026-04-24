package source.transactions.application.externalpayments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.notification.model.NotificationKind;
import source.notification.model.NotificationSeverity;
import source.notification.model.UserNotificationPayload;
import source.transactions.dto.ExternalTransferResponseDTO;
import source.transactions.dto.OnchainSendRequestDTO;
import source.transactions.exception.ExternalPaymentsExceptions;
import source.transactions.model.ExternalTransferEntity;
import source.wallet.model.WalletEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class SendOnchainPaymentUseCase {

    private final ExternalPaymentsWalletPort walletPort;
    private final ExternalPaymentsLedgerPort ledgerPort;
    private final ExternalTransfersPort externalTransfersPort;
    private final ExternalPaymentsNotificationPort notificationPort;
    private final ExternalPaymentsAuthorizationPort authorizationPort;
    private final ExternalPaymentsCustodyPort custodyPort;
    private final ExternalPaymentsFeePolicy feePolicy;
    private final ExternalPaymentsMath externalPaymentsMath;
    private final ExternalTransferFactory externalTransferFactory;
    private final String localAddressProviderName;

    public SendOnchainPaymentUseCase(
            ExternalPaymentsWalletPort walletPort,
            ExternalPaymentsLedgerPort ledgerPort,
            ExternalTransfersPort externalTransfersPort,
            ExternalPaymentsNotificationPort notificationPort,
            ExternalPaymentsAuthorizationPort authorizationPort,
            ExternalPaymentsCustodyPort custodyPort,
            ExternalPaymentsFeePolicy feePolicy,
            ExternalPaymentsMath externalPaymentsMath,
            ExternalTransferFactory externalTransferFactory,
            @Value("${transactions.local-address-provider-name:KEROSENE_LOCAL}") String localAddressProviderName) {
        this.walletPort = walletPort;
        this.ledgerPort = ledgerPort;
        this.externalTransfersPort = externalTransfersPort;
        this.notificationPort = notificationPort;
        this.authorizationPort = authorizationPort;
        this.custodyPort = custodyPort;
        this.feePolicy = feePolicy;
        this.externalPaymentsMath = externalPaymentsMath;
        this.externalTransferFactory = externalTransferFactory;
        this.localAddressProviderName = localAddressProviderName;
    }

    @Transactional
    public ExternalTransferResponseDTO send(Long userId, OnchainSendRequestDTO request) {
        externalPaymentsMath.validatePositiveAmount(request.amount(), "On-chain payment amount must be positive.");
        if (!externalPaymentsMath.isValidBitcoinAddress(request.toAddress())) {
            throw new ExternalPaymentsExceptions.InvalidNetworkAddress(
                    "The destination Bitcoin address is invalid for the configured "
                            + externalPaymentsMath.configuredBitcoinNetwork()
                            + " network.");
        }

        WalletEntity wallet = walletPort.requireWallet(userId, request.fromWalletName());
        if (wallet.isSelfCustodyMode()) {
            throw new IllegalStateException(
                    "Self-custody wallets are monitored only. On-chain spends must be signed outside the platform.");
        }
        ExternalPaymentsAuthorizationPort.AuthorizationResult authorization = authorizationPort.authorizeOutboundTransfer(
                userId,
                wallet,
                request.totpCode(),
                request.passkeyAssertionResponseJSON(),
                request.confirmationPassphrase());

        BigDecimal estimatedNetworkFee = feePolicy.estimateOnchainNetworkFee();
        BigDecimal platformFee = feePolicy.calculateWithdrawalFee(userId, request.amount());
        BigDecimal totalDebited = externalPaymentsMath.normalizeBtc(
                request.amount().add(estimatedNetworkFee).add(platformFee));

        ledgerPort.ensureBalance(wallet.getId(), totalDebited);

        String context = "EXTERNAL_ONCHAIN_PAYMENT:" + externalPaymentsMath.safeText(request.description());
        ledgerPort.updateBalance(wallet.getId(), totalDebited.negate(), context);

        ExternalPaymentsCustodyPort.PaymentResult payment = custodyPort.sendOnchain(
                new ExternalPaymentsCustodyPort.OnchainPaymentCommand(
                        userId,
                        wallet.getId(),
                        wallet.getName(),
                        request.toAddress(),
                        externalPaymentsMath.btcToSats(request.amount()),
                request.description(),
                authorization.platformSignature()));

        BigDecimal networkFee = payment.feeSats() > 0
                ? externalPaymentsMath.satsToBtc(payment.feeSats())
                : estimatedNetworkFee;
        if (networkFee.compareTo(estimatedNetworkFee) < 0) {
            BigDecimal feeRefund = externalPaymentsMath.normalizeBtc(estimatedNetworkFee.subtract(networkFee));
            ledgerPort.updateBalance(wallet.getId(), feeRefund, "ONCHAIN_NETWORK_FEE_REFUND");
            totalDebited = totalDebited.subtract(feeRefund);
        } else if (networkFee.compareTo(estimatedNetworkFee) > 0) {
            BigDecimal additionalFee = externalPaymentsMath.normalizeBtc(networkFee.subtract(estimatedNetworkFee));
            ledgerPort.ensureBalance(wallet.getId(), additionalFee);
            ledgerPort.updateBalance(wallet.getId(), additionalFee.negate(), "ONCHAIN_NETWORK_FEE_ADJUSTMENT");
            totalDebited = totalDebited.add(additionalFee);
        }
        String externalReference = externalPaymentsMath.firstNonBlank(payment.txid(), payment.providerReference());
        ExternalTransferEntity transfer = externalTransfersPort.save(externalTransferFactory.newTransfer(
                wallet,
                "ONCHAIN",
                "OUTBOUND_PAYMENT",
                externalPaymentsMath.firstNonBlank(payment.status(), "MEMPOOL"),
                resolveProviderName(),
                request.toAddress(),
                externalReference,
                null,
                payment.txid(),
                null,
                null,
                request.amount(),
                networkFee,
                platformFee,
                totalDebited,
                null,
                request.description()));
        transfer.setConfirmations(0);
        transfer.setDetectedAt(LocalDateTime.now());
        transfer.setProviderPayload(payment.rawPayload());
        transfer = externalTransfersPort.save(transfer);

        ledgerPort.recordPlatformFee(transfer.getId(), userId, totalDebited, platformFee);
        ledgerPort.recordHistory(new ExternalPaymentsLedgerPort.HistoryRecord(
                userId,
                wallet.getName(),
                request.toAddress(),
                "EXTERNAL_ONCHAIN_WITHDRAWAL",
                request.amount(),
                networkFee,
                transfer.getStatus(),
                payment.txid(),
                request.description(),
                LocalDateTime.now()));
        notificationPort.notifyUser(
                userId,
                UserNotificationPayload.create(
                        NotificationKind.PAYMENT_SENT,
                        NotificationSeverity.SUCCESS,
                        "Pagamento on-chain enviado",
                        "Pagamento externo enviado para " + request.toAddress() + ".",
                        "/history",
                        "external_transfer",
                        transfer.getId() != null ? transfer.getId().toString() : null,
                        Map.of(
                                "walletName", wallet.getName(),
                                "amountBtc", request.amount().toPlainString(),
                                "network", "ONCHAIN",
                                "destination", request.toAddress())));

        return externalTransferFactory.toResponseDTO(transfer);
    }

    private String resolveProviderName() {
        return custodyPort.providerName() != null ? custodyPort.providerName() : localAddressProviderName;
    }
}
