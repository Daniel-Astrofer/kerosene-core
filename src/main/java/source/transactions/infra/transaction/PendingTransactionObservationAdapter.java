package source.transactions.infra.transaction;

import org.springframework.stereotype.Component;
import source.ledger.repository.LedgerTransactionHistoryRepository;
import source.notification.l10n.NotificationMessageKey;
import source.notification.l10n.NotificationMessages;
import source.notification.model.NotificationKind;
import source.notification.model.NotificationSeverity;
import source.notification.service.NotificationService;
import source.transactions.application.transaction.monitoring.PendingTransactionObservationPort;
import source.transactions.model.PendingTransaction;

import java.util.Map;

@Component
public class PendingTransactionObservationAdapter implements PendingTransactionObservationPort {

    private final NotificationService notificationService;
    private final LedgerTransactionHistoryRepository historyRepository;

    public PendingTransactionObservationAdapter(
            NotificationService notificationService,
            LedgerTransactionHistoryRepository historyRepository) {
        this.notificationService = notificationService;
        this.historyRepository = historyRepository;
    }

    @Override
    public void notifyPendingDepositDetected(PendingTransaction transaction) {
        notificationService.notifyUser(
                transaction.getUserId(),
                NotificationMessages.payload(
                        NotificationKind.DEPOSIT_DETECTED,
                        NotificationSeverity.INFO,
                        NotificationMessageKey.PENDING_DEPOSIT_DETECTED,
                        "/deposits",
                        "transaction",
                        transaction.getTxid(),
                        Map.of("amountBtc", transaction.getAmount().toPlainString()),
                        transaction.getAmount().toPlainString()));
    }

    @Override
    public void syncConfirmations(String txid, int confirmations) {
        historyRepository.findByBlockchainTxid(txid).ifPresent(history -> {
            history.setConfirmations(confirmations);
            historyRepository.save(history);
        });
    }
}
