package source.transactions.infra.transaction;

import org.springframework.stereotype.Component;
import source.transactions.application.transaction.TransactionPendingPort;
import source.transactions.model.PendingTransaction;
import source.transactions.repository.PendingTransactionRedisRepository;

import java.util.List;
import java.util.Optional;

@Component
public class PendingTransactionRedisAdapter implements TransactionPendingPort {

    private final PendingTransactionRedisRepository pendingTransactionRedisRepository;

    public PendingTransactionRedisAdapter(PendingTransactionRedisRepository pendingTransactionRedisRepository) {
        this.pendingTransactionRedisRepository = pendingTransactionRedisRepository;
    }

    @Override
    public PendingTransaction save(PendingTransaction transaction) {
        return pendingTransactionRedisRepository.save(transaction);
    }

    @Override
    public Optional<PendingTransaction> findByTxid(String txid) {
        return Optional.ofNullable(pendingTransactionRedisRepository.findByTxid(txid));
    }

    @Override
    public List<PendingTransaction> findPendingTransactions() {
        return pendingTransactionRedisRepository.findByStatus("PENDING");
    }

    @Override
    public List<PendingTransaction> findTransactionsByUserId(Long userId) {
        return pendingTransactionRedisRepository.findByUserId(userId);
    }
}
