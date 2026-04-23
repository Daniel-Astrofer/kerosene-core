package source.transactions.infra.transaction;

import org.springframework.stereotype.Component;
import source.ledger.repository.LedgerTransactionHistoryRepository;
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
        String title = "Depósito Identificado";
        String body = String.format(
                "Um depósito de %s BTC está pendente na rede. Aguardando confirmações de segurança.",
                transaction.getAmount().toPlainString());
        notificationService.notifyUser(
                transaction.getUserId(),
                NotificationKind.DEPOSIT_DETECTED,
                NotificationSeverity.INFO,
                title,
                body,
                "/deposits",
                "transaction",
                transaction.getTxid(),
                Map.of("amountBtc", transaction.getAmount().toPlainString()));
    }

    @Override
    public void syncConfirmations(String txid, int confirmations) {
        historyRepository.findByBlockchainTxid(txid).ifPresent(history -> {
            history.setConfirmations(confirmations);
            historyRepository.save(history);
        });
    }
}
