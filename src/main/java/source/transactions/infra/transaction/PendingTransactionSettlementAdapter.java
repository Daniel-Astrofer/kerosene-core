package source.transactions.infra.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import source.common.infra.logging.LogSanitizer;
import source.common.idempotency.IdempotencyKeyBuilder;
import source.ledger.entity.LedgerTransactionHistory;
import source.ledger.repository.LedgerTransactionHistoryRepository;
import source.ledger.service.LedgerService;
import source.notification.model.NotificationKind;
import source.notification.model.NotificationSeverity;
import source.notification.service.NotificationService;
import source.transactions.application.transaction.monitoring.PendingTransactionSettlementPort;
import source.transactions.model.PendingTransaction;
import source.transactions.service.ProcessedTransactionService;
import source.wallet.application.port.in.WalletLookupPort;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletCardProfileService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
public class PendingTransactionSettlementAdapter implements PendingTransactionSettlementPort {

    private static final Logger log = LoggerFactory.getLogger(PendingTransactionSettlementAdapter.class);

    private final WalletLookupPort walletLookupPort;
    private final LedgerService ledgerService;
    private final NotificationService notificationService;
    private final LedgerTransactionHistoryRepository historyRepository;
    private final WalletCardProfileService walletCardProfileService;
    private final ProcessedTransactionService processedTransactionService;

    public PendingTransactionSettlementAdapter(
            WalletLookupPort walletLookupPort,
            LedgerService ledgerService,
            NotificationService notificationService,
            LedgerTransactionHistoryRepository historyRepository,
            WalletCardProfileService walletCardProfileService,
            ProcessedTransactionService processedTransactionService) {
        this.walletLookupPort = walletLookupPort;
        this.ledgerService = ledgerService;
        this.notificationService = notificationService;
        this.historyRepository = historyRepository;
        this.walletCardProfileService = walletCardProfileService;
        this.processedTransactionService = processedTransactionService;
    }

    @Override
    public void settleConfirmedTransaction(PendingTransaction transaction, int confirmations) {
        processedTransactionService.processOnce(
                IdempotencyKeyBuilder.build("blockchain-monitor", transaction.getTxid(), transaction.getToAddress()),
                "BLOCKCHAIN_MONITOR",
                () -> applyTransactionEffects(transaction, confirmations));
    }

    private void applyTransactionEffects(PendingTransaction transaction, int confirmations) {
        applySenderEffects(transaction, confirmations);
        applyReceiverEffects(transaction, confirmations);
    }

    private void applySenderEffects(PendingTransaction transaction, int confirmations) {
        try {
            WalletEntity senderWallet = walletLookupPort.findByDepositAddress(transaction.getFromAddress());
            if (senderWallet == null) {
                return;
            }

            long feeSats = transaction.getFeeSatoshis() != null ? transaction.getFeeSatoshis() : 0L;
            BigDecimal totalDeduction = transaction.getAmount().add(
                    BigDecimal.valueOf(feeSats).divide(BigDecimal.valueOf(100_000_000)));

            ledgerService.updateBalance(
                    senderWallet.getId(),
                    totalDeduction.negate(),
                    "transfer_out: " + transaction.getTxid());

            log.info("Deducted BTC from sender wallet {} for txRef={}",
                    senderWallet.getId(),
                    LogSanitizer.fingerprint(transaction.getTxid()));

            try {
                notificationService.notifyUser(
                        senderWallet.getUser().getId(),
                        NotificationKind.PAYMENT_SENT,
                        NotificationSeverity.SUCCESS,
                        "Transferência Confirmada",
                        String.format(
                                "A transferência de %s BTC da carteira '%s' foi confirmada na rede Blockchain.",
                                transaction.getAmount().toPlainString(),
                                senderWallet.getName()),
                        "/history",
                        "transaction",
                        transaction.getTxid(),
                        Map.of(
                                "walletName", senderWallet.getName(),
                                "amountBtc", transaction.getAmount().toPlainString(),
                                "confirmations", String.valueOf(confirmations)));

                historyRepository.findByBlockchainTxid(transaction.getTxid()).ifPresent(history -> {
                    history.setStatus("CONCLUDED");
                    history.setConfirmations(confirmations);
                    historyRepository.save(history);
                });
            } catch (Exception ex) {
                log.error("Erro ao notificar confirmação de envio: {}", ex.getMessage());
            }
        } catch (Exception ex) {
            log.error("Failed to update sender balance for txRef={}: {}",
                    LogSanitizer.fingerprint(transaction.getTxid()), ex.getMessage());
            throw ex;
        }
    }

    private void applyReceiverEffects(PendingTransaction transaction, int confirmations) {
        try {
            WalletEntity receiverWallet = walletLookupPort.findByDepositAddress(transaction.getToAddress());
            if (receiverWallet == null) {
                return;
            }

            BigDecimal depositFee = walletCardProfileService.calculateDepositFee(
                    receiverWallet.getUser().getId(),
                    transaction.getAmount());
            BigDecimal netCredit = transaction.getAmount().subtract(depositFee);

            ledgerService.updateBalance(
                    receiverWallet.getId(),
                    netCredit,
                    "transfer_in: " + transaction.getTxid());

            log.info("Credited BTC to receiver wallet {} for txRef={}",
                    receiverWallet.getId(),
                    LogSanitizer.fingerprint(transaction.getTxid()));

            try {
                notificationService.notifyUser(
                        receiverWallet.getUser().getId(),
                        NotificationKind.DEPOSIT_CONFIRMED,
                        NotificationSeverity.SUCCESS,
                        "Depósito Confirmado",
                        String.format(
                                "O aporte bruto de %s BTC na carteira '%s' foi confirmado. Liquido creditado: %s BTC.",
                                transaction.getAmount().toPlainString(),
                                receiverWallet.getName(),
                                netCredit.toPlainString()),
                        "/deposits",
                        "transaction",
                        transaction.getTxid(),
                        Map.of(
                                "walletName", receiverWallet.getName(),
                                "grossAmountBtc", transaction.getAmount().toPlainString(),
                                "netAmountBtc", netCredit.toPlainString(),
                                "confirmations", String.valueOf(confirmations)));

                upsertDepositHistory(transaction, confirmations, depositFee, netCredit, receiverWallet);
            } catch (Exception ex) {
                log.error("Erro ao notificar confirmação de depósito: {}", ex.getMessage());
            }
        } catch (Exception ex) {
            log.error("Failed to update receiver balance for txRef={}: {}",
                    LogSanitizer.fingerprint(transaction.getTxid()), ex.getMessage());
            throw ex;
        }
    }

    private void upsertDepositHistory(
            PendingTransaction transaction,
            int confirmations,
            BigDecimal depositFee,
            BigDecimal netCredit,
            WalletEntity receiverWallet) {
        historyRepository.findByBlockchainTxid(transaction.getTxid()).ifPresentOrElse(history -> {
            history.setStatus("CONCLUDED");
            history.setConfirmations(confirmations);
            history.setContext(buildDepositContext(transaction, depositFee, netCredit));
            historyRepository.save(history);
        }, () -> {
            LedgerTransactionHistory history = new LedgerTransactionHistory();
            history.setId(UUID.randomUUID());
            history.setAmount(transaction.getAmount());
            history.setCreatedAt(LocalDateTime.now());
            history.setContext(buildDepositContext(transaction, depositFee, netCredit));
            history.setReceiverUserId(receiverWallet.getUser().getId());
            history.setReceiverIdentifier(receiverWallet.getName());
            history.setSenderIdentifier(
                    transaction.getFromAddress() != null ? transaction.getFromAddress() : "UNKNOWN");
            history.setBlockchainTxid(transaction.getTxid());
            history.setTransactionType("EXTERNAL_DEPOSIT");
            history.setStatus("CONCLUDED");
            history.setConfirmations(confirmations);
            historyRepository.save(history);
        });
    }

    private String buildDepositContext(PendingTransaction transaction, BigDecimal depositFee, BigDecimal netCredit) {
        return "On-Chain Deposit: External Bitcoin Deposit | gross="
                + transaction.getAmount().toPlainString()
                + " BTC | fee=" + depositFee.toPlainString()
                + " BTC | net=" + netCredit.toPlainString() + " BTC";
    }
}
