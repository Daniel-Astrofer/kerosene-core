package source.transactions.application.externalpayments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.common.idempotency.IdempotencyKeyBuilder;
import source.common.validation.FinancialAmountValidator;
import source.notification.l10n.NotificationMessageKey;
import source.notification.l10n.NotificationMessages;
import source.notification.model.NotificationKind;
import source.notification.model.NotificationSeverity;
import source.transactions.dto.ExternalTransferResponseDTO;
import source.transactions.dto.LightningPaymentRequestDTO;
import source.transactions.exception.ExternalPaymentsExceptions;
import source.transactions.infra.CustodyGateway;
import source.transactions.infra.LightningPaymentGateway;
import source.transactions.model.ExternalTransferEntity;
import source.transactions.service.ProcessedTransactionService;
import source.treasury.service.TreasuryService;
import source.wallet.model.WalletEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class PayLightningPaymentUseCase {

    private final ExternalPaymentsWalletPort walletPort;
    private final ExternalPaymentsLedgerPort ledgerPort;
    private final ExternalTransfersPort externalTransfersPort;
    private final ExternalPaymentsNotificationPort notificationPort;
    private final ExternalPaymentsAuthorizationPort authorizationPort;
    private final LightningPaymentGateway lightningPaymentGateway;
    private final ExternalPaymentsFeePolicy feePolicy;
    private final ExternalPaymentsMath externalPaymentsMath;
    private final ExternalTransferFactory externalTransferFactory;
    private final TreasuryService treasuryService;
    private final ProcessedTransactionService processedTransactionService;
    private final source.transactions.service.ExternalProviderOutboxService outboxService;
    private final String localAddressProviderName;

    public PayLightningPaymentUseCase(
            ExternalPaymentsWalletPort walletPort,
            ExternalPaymentsLedgerPort ledgerPort,
            ExternalTransfersPort externalTransfersPort,
            ExternalPaymentsNotificationPort notificationPort,
            ExternalPaymentsAuthorizationPort authorizationPort,
            @Qualifier("externalLightningPaymentGateway")
            LightningPaymentGateway lightningPaymentGateway,
            ExternalPaymentsFeePolicy feePolicy,
            ExternalPaymentsMath externalPaymentsMath,
            ExternalTransferFactory externalTransferFactory,
            TreasuryService treasuryService,
            ProcessedTransactionService processedTransactionService,
            source.transactions.service.ExternalProviderOutboxService outboxService,
            @Value("${transactions.local-address-provider-name:KEROSENE_LOCAL}") String localAddressProviderName) {
        this.walletPort = walletPort;
        this.ledgerPort = ledgerPort;
        this.externalTransfersPort = externalTransfersPort;
        this.notificationPort = notificationPort;
        this.authorizationPort = authorizationPort;
        this.lightningPaymentGateway = lightningPaymentGateway;
        this.feePolicy = feePolicy;
        this.externalPaymentsMath = externalPaymentsMath;
        this.externalTransferFactory = externalTransferFactory;
        this.treasuryService = treasuryService;
        this.processedTransactionService = processedTransactionService;
        this.outboxService = outboxService;
        this.localAddressProviderName = localAddressProviderName;
    }

    @Transactional
    public ExternalTransferResponseDTO pay(Long userId, LightningPaymentRequestDTO request) {
        requireIdempotencyKey(request.idempotencyKey());
        String idempotencyRef = IdempotencyKeyBuilder.build(
                "external-lightning-pay",
                String.valueOf(userId),
                request.idempotencyKey());
        AtomicReference<ExternalTransferResponseDTO> response = new AtomicReference<>();
        boolean processed = processedTransactionService.processOnce(
                idempotencyRef,
                "EXTERNAL_LIGHTNING_PAY",
                () -> response.set(payOnce(userId, request, idempotencyRef)));
        if (!processed) {
            throw new ExternalPaymentsExceptions.DuplicateExternalPayment(
                    "Lightning payment already submitted for this idempotency key.");
        }
        return response.get();
    }

    private ExternalTransferResponseDTO payOnce(Long userId, LightningPaymentRequestDTO request, String idempotencyRef) {
        FinancialAmountValidator.requirePositiveBtc(request.amount(), "amount");
        externalPaymentsMath.validatePositiveAmount(request.amount(), "Lightning payment amount must be positive.");
        if (request.paymentRequest() == null || request.paymentRequest().isBlank()) {
            throw new IllegalArgumentException("A valid Lightning invoice is required.");
        }
        treasuryService.assertLightningOutboundAvailable(externalPaymentsMath.btcToSats(request.amount()));

        WalletEntity wallet = walletPort.requireWallet(userId, request.fromWalletName());
        if (wallet.isSelfCustodyMode()) {
            throw new IllegalStateException(
                    "Self-custody wallets cannot originate custodial Lightning payments.");
        }
        ExternalPaymentsAuthorizationPort.AuthorizationResult authorization = authorizationPort.authorizeOutboundTransfer(
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

        ExternalTransferEntity transfer = externalTransfersPort.save(externalTransferFactory.newTransfer(
                wallet,
                "LIGHTNING",
                "OUTBOUND_PAYMENT",
                "PROVIDER_PENDING",
                resolveProviderName(),
                request.paymentRequest(),
                null,
                null,
                null,
                null,
                request.paymentRequest(),
                request.amount(),
                reservedNetworkFee,
                platformFee,
                reservedTotal,
                null,
                request.description()));
        transfer.setDetectedAt(LocalDateTime.now());
        transfer = externalTransfersPort.save(transfer);
        source.transactions.model.ExternalProviderOutboxEntity outbox = outboxService.enqueue(
                transfer.getId(),
                "LIGHTNING_PAY",
                idempotencyRef,
                "{\"amountSats\":" + externalPaymentsMath.btcToSats(request.amount())
                        + ",\"maxFeeSats\":" + externalPaymentsMath.btcToSats(reservedNetworkFee) + "}");

        CustodyGateway.PaymentResult payment;
        try {
            payment = lightningPaymentGateway.payLightning(
                    new CustodyGateway.LightningPaymentCommand(
                            userId,
                            wallet.getId(),
                            wallet.getName(),
                            request.paymentRequest(),
                            externalPaymentsMath.btcToSats(request.amount()),
                            externalPaymentsMath.btcToSats(reservedNetworkFee),
                            request.description(),
                            idempotencyRef,
                            authorization.platformSignature()));
        } catch (RuntimeException providerFailure) {
            ledgerPort.updateBalance(wallet.getId(), reservedTotal, "LIGHTNING_PAYMENT_PROVIDER_FAILURE_COMPENSATION");
            transfer.setStatus("PROVIDER_FAILED");
            transfer.setProviderPayload("providerFailure=" + providerFailure.getClass().getSimpleName());
            externalTransfersPort.save(transfer);
            outboxService.markFailed(outbox.getId(), providerFailure.getMessage(), false);
            throw providerFailure;
        }

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
        transfer.setStatus(externalPaymentsMath.firstNonBlank(payment.status(), "SETTLED"));
        transfer.setExternalReference(payment.providerReference());
        transfer.setBlockchainTxid(payment.txid());
        transfer.setPaymentHash(externalReference);
        transfer.setNetworkFeeBtc(actualFee);
        transfer.setTotalDebitedBtc(reservedTotal);
        transfer.setDetectedAt(LocalDateTime.now());
        transfer.setProviderPayload(payment.rawPayload());
        if ("SETTLED".equalsIgnoreCase(transfer.getStatus()) || "COMPLETED".equalsIgnoreCase(transfer.getStatus())) {
            transfer.setSettledAt(LocalDateTime.now());
        }
        transfer = externalTransfersPort.save(transfer);
        outboxService.markDispatched(outbox.getId(), externalReference);

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
                NotificationMessages.payload(
                        NotificationKind.PAYMENT_SENT,
                        NotificationSeverity.SUCCESS,
                        NotificationMessageKey.EXTERNAL_LIGHTNING_PAYMENT_SENT,
                        "/history",
                        "external_transfer",
                        transfer.getId() != null ? transfer.getId().toString() : null,
                        Map.of(
                                "walletName", wallet.getName(),
                                "amountBtc", request.amount().toPlainString(),
                                "network", "LIGHTNING")));

        return externalTransferFactory.toResponseDTO(transfer);
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required for Lightning payments.");
        }
        if (idempotencyKey.length() > 96) {
            throw new IllegalArgumentException("idempotencyKey must have at most 96 characters.");
        }
    }

    private String resolveProviderName() {
        return lightningPaymentGateway.providerName() != null ? lightningPaymentGateway.providerName() : localAddressProviderName;
    }
}
