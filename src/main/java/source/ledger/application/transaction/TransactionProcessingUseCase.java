package source.ledger.application.transaction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
        TransactionContext context = new TransactionContext(transaction);
        new DefaultTransactionHandlerChain(handlers).next(context);
    }
}
