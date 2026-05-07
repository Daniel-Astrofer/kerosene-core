package source.ledger.infra.transaction;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import source.ledger.application.transaction.TransactionIdempotencyPort;
import source.transactions.model.ProcessedTransactionEntity;
import source.transactions.repository.ProcessedTransactionRepository;

import java.util.concurrent.TimeUnit;

@Component
public class RedisTransactionIdempotencyAdapter implements TransactionIdempotencyPort {

    private final ProcessedTransactionRepository processedTransactionRepository;

    public RedisTransactionIdempotencyAdapter(ProcessedTransactionRepository processedTransactionRepository) {
        this.processedTransactionRepository = processedTransactionRepository;
    }

    @Override
    public boolean reserve(String key, long ttl, TimeUnit unit) {
        try {
            processedTransactionRepository.saveAndFlush(new ProcessedTransactionEntity(key, "LEDGER_IDEMPOTENCY"));
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
    }
}
