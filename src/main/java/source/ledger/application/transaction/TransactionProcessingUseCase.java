package source.ledger.application.transaction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.common.validation.FinancialAmountValidator;
import source.ledger.dto.TransactionDTO;

import java.util.List;

@Service
public class TransactionProcessingUseCase {

    private final List<TransactionHandler> handlers;

    public TransactionProcessingUseCase(List<TransactionHandler> handlers) {
        this.handlers = handlers;
    }

    @Transactional
    public void process(TransactionDTO transaction) {
        validateTransaction(transaction);
        TransactionContext context = new TransactionContext(transaction);
        new DefaultTransactionHandlerChain(handlers).next(context);
    }

    private void validateTransaction(TransactionDTO transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("transaction is required.");
        }
        requireText(transaction.getSender(), "sender");
        requireText(transaction.getReceiver(), "receiver");
        requireText(transaction.getIdempotencyKey(), "idempotencyKey");
        if (transaction.getIdempotencyKey().length() > 96) {
            throw new IllegalArgumentException("idempotencyKey must have at most 96 characters.");
        }
        FinancialAmountValidator.requirePositiveBtc(transaction.getAmount(), "amount");
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
    }
}
