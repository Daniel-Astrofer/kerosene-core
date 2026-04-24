package source.transactions.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.auth.application.service.account.AccountActivationService;
import source.notification.model.NotificationKind;
import source.notification.model.NotificationSeverity;
import source.notification.model.UserNotificationPayload;
import source.transactions.application.externalpayments.ExternalPaymentsLedgerPort;
import source.transactions.application.externalpayments.ExternalPaymentsMath;
import source.transactions.application.externalpayments.ExternalPaymentsNotificationPort;
import source.transactions.application.externalpayments.ExternalTransfersPort;
import source.transactions.model.ExternalTransferEntity;
import source.wallet.service.WalletCardProfileService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

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
        return processedTransactionService.processOnce(txid, "INBOUND_ONCHAIN", () -> {
            BigDecimal grossAmount = externalPaymentsMath.satsToBtc(grossSats);
            settle(
                    transfer,
                    grossAmount,
                    txid,
                    Math.max(0, confirmations),
                    "EXTERNAL_ONCHAIN_DEPOSIT",
                    contextMessage != null ? contextMessage : "Deposito on-chain confirmado na rede Bitcoin.");
        });
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
        return processedTransactionService.processOnce(paymentHash, "INBOUND_LIGHTNING", () -> {
            BigDecimal grossAmount = externalPaymentsMath.satsToBtc(grossSats);
            settle(
                    transfer,
                    grossAmount,
                    paymentHash,
                    transfer.getConfirmations() != null ? transfer.getConfirmations() : 0,
                    "EXTERNAL_LIGHTNING_DEPOSIT",
                    contextMessage != null ? contextMessage : "Deposito Lightning liquidado com sucesso.");
        });
    }

    private void settle(
            ExternalTransferEntity transfer,
            BigDecimal grossAmount,
            String settlementReference,
            int confirmations,
            String historyType,
            String notificationBody) {
        if (grossAmount == null || grossAmount.signum() <= 0) {
            log.warn("[ExternalInboundSettlement] Transfer {} has no positive gross amount.", transfer.getId());
            return;
        }

        BigDecimal normalizedGross = externalPaymentsMath.normalizeBtc(grossAmount);
        BigDecimal depositFee = walletCardProfileService.calculateDepositFee(
                transfer.getUserId(),
                normalizedGross);
        BigDecimal netCredit = externalPaymentsMath.normalizeBtc(normalizedGross.subtract(depositFee));
        if (netCredit.signum() <= 0) {
            log.warn("[ExternalInboundSettlement] Transfer {} net credit is non-positive.", transfer.getId());
            return;
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
        externalTransfersPort.save(transfer);
        accountActivationService.activateUser(transfer.getUserId());
        networkTransferEventService.info(
                transfer,
                "TRANSFER_SETTLED",
                settlementReference,
                buildCompletionContext(transfer, normalizedGross, depositFee, netCredit));

        notificationPort.notifyUser(
                transfer.getUserId(),
                UserNotificationPayload.create(
                        NotificationKind.DEPOSIT_CONFIRMED,
                        NotificationSeverity.SUCCESS,
                        "Deposito confirmado",
                        notificationBody + " Liquido creditado: " + netCredit.toPlainString() + " BTC.",
                        "/deposits",
                        "external_transfer",
                        transfer.getId() != null ? transfer.getId().toString() : null,
                        Map.of(
                                "grossAmountBtc", normalizedGross.toPlainString(),
                                "netAmountBtc", netCredit.toPlainString(),
                                "network", transfer.getNetwork())));
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
}
