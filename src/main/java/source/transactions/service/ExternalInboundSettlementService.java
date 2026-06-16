package source.transactions.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.auth.application.service.account.AccountActivationService;
import source.notification.l10n.NotificationMessageKey;
import source.notification.l10n.NotificationMessages;
import source.notification.model.NotificationKind;
import source.notification.model.NotificationSeverity;
import source.transactions.application.externalpayments.ExternalPaymentsLedgerPort;
import source.transactions.application.externalpayments.ExternalPaymentsMath;
import source.transactions.application.externalpayments.ExternalPaymentsNotificationPort;
import source.transactions.application.externalpayments.ExternalTransfersPort;
import source.transactions.model.ExternalTransferEntity;
import source.wallet.service.WalletCardProfileService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ExternalInboundSettlementService {

    private static final Logger log = LoggerFactory.getLogger(ExternalInboundSettlementService.class);

    private final ExternalTransfersPort externalTransfersPort;
    private final ExternalPaymentsLedgerPort ledgerPort;
    private final ExternalPaymentsNotificationPort notificationPort;
    private final ExternalPaymentsMath externalPaymentsMath;
    private final WalletCardProfileService walletCardProfileService;
    private final AccountActivationService accountActivationService;
    private final ProcessedTransactionService processedTransactionService;
    private final NetworkTransferEventService networkTransferEventService;

    public ExternalInboundSettlementService(
            ExternalTransfersPort externalTransfersPort,
            ExternalPaymentsLedgerPort ledgerPort,
            ExternalPaymentsNotificationPort notificationPort,
            ExternalPaymentsMath externalPaymentsMath,
            WalletCardProfileService walletCardProfileService,
            AccountActivationService accountActivationService,
            ProcessedTransactionService processedTransactionService,
            NetworkTransferEventService networkTransferEventService) {
        this.externalTransfersPort = externalTransfersPort;
        this.ledgerPort = ledgerPort;
        this.notificationPort = notificationPort;
        this.externalPaymentsMath = externalPaymentsMath;
        this.walletCardProfileService = walletCardProfileService;
        this.accountActivationService = accountActivationService;
        this.processedTransactionService = processedTransactionService;
        this.networkTransferEventService = networkTransferEventService;
    }

    @Transactional
    public boolean settleOnchainInbound(
            ExternalTransferEntity transfer,
            long grossSats,
            String txid,
            int confirmations,
            String contextMessage) {
        if (transfer == null || txid == null || txid.isBlank()) {
            return false;
        }
        AtomicBoolean settled = new AtomicBoolean(false);
        boolean processed = processedTransactionService.processOnce(txid, "INBOUND_ONCHAIN", () -> {
            BigDecimal grossAmount = externalPaymentsMath.satsToBtc(grossSats);
            settled.set(settle(
                    transfer,
                    grossAmount,
                    txid,
                    Math.max(0, confirmations),
                    "EXTERNAL_ONCHAIN_DEPOSIT",
                    contextMessage != null ? contextMessage : "Deposito on-chain confirmado na rede Bitcoin."));
        });
        return processed ? settled.get() : isAlreadySettled(transfer);
    }

    @Transactional
    public boolean settleLightningInbound(
            ExternalTransferEntity transfer,
            long grossSats,
            String paymentHash,
            String contextMessage) {
        if (transfer == null || paymentHash == null || paymentHash.isBlank()) {
            return false;
        }
        AtomicBoolean settled = new AtomicBoolean(false);
        boolean processed = processedTransactionService.processOnce(paymentHash, "INBOUND_LIGHTNING", () -> {
            BigDecimal grossAmount = externalPaymentsMath.satsToBtc(grossSats);
            settled.set(settle(
                    transfer,
                    grossAmount,
                    paymentHash,
                    transfer.getConfirmations() != null ? transfer.getConfirmations() : 0,
                    "EXTERNAL_LIGHTNING_DEPOSIT",
                    contextMessage != null ? contextMessage : "Deposito Lightning liquidado com sucesso."));
        });
        return processed ? settled.get() : isAlreadySettled(transfer);
    }

    private boolean settle(
            ExternalTransferEntity transfer,
            BigDecimal grossAmount,
            String settlementReference,
            int confirmations,
            String historyType,
            String notificationBody) {
        if (grossAmount == null || grossAmount.signum() <= 0) {
            log.warn("[ExternalInboundSettlement] Transfer {} has no positive gross amount.", transfer.getId());
            markAutoResolutionPending(
                    transfer,
                    grossAmount,
                    settlementReference,
                    "INBOUND_INVALID_SETTLEMENT_AMOUNT",
                    "Settlement amount must be positive.");
            return false;
        }

        BigDecimal normalizedGross = externalPaymentsMath.normalizeBtc(grossAmount);
        BigDecimal expectedAmount = transfer.getExpectedAmountBtc();
        boolean amountMismatch = expectedAmount != null
                && expectedAmount.signum() > 0
                && expectedAmount.compareTo(normalizedGross) != 0;
        if (amountMismatch && isLightningTransfer(transfer)) {
            markAutoResolutionPending(
                    transfer,
                    normalizedGross,
                    settlementReference,
                    "INBOUND_AMOUNT_MISMATCH",
                    "expected=" + expectedAmount.toPlainString()
                            + " BTC | observed=" + normalizedGross.toPlainString() + " BTC");
            return false;
        }
        BigDecimal depositFee = externalPaymentsMath.normalizeBtc(walletCardProfileService.calculateDepositFee(
                transfer.getUserId(),
                normalizedGross));
        BigDecimal netCredit = externalPaymentsMath.normalizeBtc(normalizedGross.subtract(depositFee));
        if (netCredit.signum() <= 0) {
            log.warn("[ExternalInboundSettlement] Transfer {} net credit is non-positive.", transfer.getId());
            markAutoResolutionPending(
                    transfer,
                    normalizedGross,
                    settlementReference,
                    "INBOUND_NON_POSITIVE_NET_CREDIT",
                    "gross=" + normalizedGross.toPlainString()
                            + " BTC | fee=" + depositFee.toPlainString() + " BTC");
            return false;
        }

        ledgerPort.updateBalance(
                transfer.getWalletId(),
                netCredit,
                "INBOUND_TRANSFER:" + transfer.getId());
        ledgerPort.recordHistory(new ExternalPaymentsLedgerPort.HistoryRecord(
                transfer.getUserId(),
                transfer.getProvider(),
                transfer.getWalletNameSnapshot(),
                historyType,
                netCredit,
                BigDecimal.ZERO,
                "COMPLETED",
                settlementReference,
                buildCompletionContext(transfer, normalizedGross, depositFee, netCredit),
                LocalDateTime.now()));
        ledgerPort.recordPlatformFee(transfer.getId(), transfer.getUserId(), normalizedGross, depositFee);

        transfer.setAmountBtc(normalizedGross);
        transfer.setPlatformFeeBtc(depositFee);
        transfer.setTotalDebitedBtc(normalizedGross);
        transfer.setStatus("COMPLETED");
        transfer.setConfirmations(confirmations);
        transfer.setSettledAt(LocalDateTime.now());
        transfer.setContext(appendContext(
                transfer.getContext(),
                "Inbound transfer settled and credited. gross=" + normalizedGross.toPlainString()
                        + " BTC | fee=" + depositFee.toPlainString()
                        + " BTC | net=" + netCredit.toPlainString() + " BTC"));
        if (amountMismatch) {
            transfer.setContext(appendContext(
                    transfer.getContext(),
                    "Expected amount mismatch. expected=" + expectedAmount.toPlainString()
                            + " BTC | observed=" + normalizedGross.toPlainString() + " BTC"));
            networkTransferEventService.warn(
                    transfer,
                    "INBOUND_AMOUNT_MISMATCH",
                    settlementReference,
                    "expected=" + expectedAmount.toPlainString()
                            + " BTC | observed=" + normalizedGross.toPlainString() + " BTC");
        }
        externalTransfersPort.save(transfer);
        accountActivationService.activateUser(transfer.getUserId());
        networkTransferEventService.info(
                transfer,
                "TRANSFER_SETTLED",
                settlementReference,
                buildCompletionContext(transfer, normalizedGross, depositFee, netCredit));

        notificationPort.notifyUser(
                transfer.getUserId(),
                NotificationMessages.payload(
                        NotificationKind.DEPOSIT_CONFIRMED,
                        NotificationSeverity.SUCCESS,
                        inboundSettlementMessageKey(transfer, historyType, notificationBody),
                        "/deposits",
                        "external_transfer",
                        transfer.getId() != null ? transfer.getId().toString() : null,
                        Map.of(
                                "grossAmountBtc", normalizedGross.toPlainString(),
                                "netAmountBtc", netCredit.toPlainString(),
                                "network", transfer.getNetwork()),
                        netCredit.toPlainString()));
        return true;
    }

    private NotificationMessageKey inboundSettlementMessageKey(
            ExternalTransferEntity transfer,
            String historyType,
            String notificationBody) {
        boolean lightning = isLightningTransfer(transfer) || "EXTERNAL_LIGHTNING_DEPOSIT".equalsIgnoreCase(historyType);
        boolean reconciled = notificationBody != null && notificationBody.contains(" via ");
        if (lightning) {
            return reconciled
                    ? NotificationMessageKey.EXTERNAL_LIGHTNING_DEPOSIT_RECONCILED
                    : NotificationMessageKey.EXTERNAL_LIGHTNING_DEPOSIT_CONFIRMED;
        }
        return reconciled
                ? NotificationMessageKey.EXTERNAL_ONCHAIN_DEPOSIT_RECONCILED
                : NotificationMessageKey.EXTERNAL_ONCHAIN_DEPOSIT_CONFIRMED;
    }

    private String buildCompletionContext(
            ExternalTransferEntity transfer,
            BigDecimal grossAmount,
            BigDecimal depositFee,
            BigDecimal netCredit) {
        String prefix = "LIGHTNING".equalsIgnoreCase(transfer.getNetwork())
                ? "Lightning Deposit"
                : "On-Chain Deposit";
        return prefix + " | gross=" + grossAmount.toPlainString()
                + " BTC | fee=" + depositFee.toPlainString()
                + " BTC | net=" + netCredit.toPlainString() + " BTC";
    }

    private String appendContext(String current, String addition) {
        if (addition == null || addition.isBlank()) {
            return current;
        }
        if (current == null || current.isBlank()) {
            return addition;
        }
        if (current.contains(addition)) {
            return current;
        }
        return current + " | " + addition;
    }

    private void markAutoResolutionPending(
            ExternalTransferEntity transfer,
            BigDecimal observedAmount,
            String settlementReference,
            String eventType,
            String reason) {
        if (observedAmount != null && observedAmount.signum() > 0) {
            transfer.setAmountBtc(externalPaymentsMath.normalizeBtc(observedAmount));
        }
        transfer.setStatus("AUTO_RESOLUTION_PENDING");
        transfer.setDetectedAt(transfer.getDetectedAt() != null ? transfer.getDetectedAt() : LocalDateTime.now());
        transfer.setContext(appendContext(
                transfer.getContext(),
                "Inbound settlement requires manual reconciliation. " + reason));
        externalTransfersPort.save(transfer);
        networkTransferEventService.warn(
                transfer,
                eventType,
                settlementReference,
                reason);
    }

    private boolean isAlreadySettled(ExternalTransferEntity transfer) {
        return transfer != null
                && ("COMPLETED".equalsIgnoreCase(transfer.getStatus()) || transfer.getSettledAt() != null);
    }

    private boolean isLightningTransfer(ExternalTransferEntity transfer) {
        return transfer != null && "LIGHTNING".equalsIgnoreCase(transfer.getNetwork());
    }
}
