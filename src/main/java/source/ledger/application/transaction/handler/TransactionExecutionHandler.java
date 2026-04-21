package source.ledger.application.transaction.handler;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import source.ledger.application.transaction.TransactionContext;
import source.ledger.application.transaction.TransactionHandler;
import source.ledger.application.transaction.TransactionHandlerChain;
import source.ledger.application.transaction.TransactionLedgerService;

@Component
@Order(60)
public class TransactionExecutionHandler implements TransactionHandler {

    private final TransactionLedgerService transactionLedgerService;

    public TransactionExecutionHandler(TransactionLedgerService transactionLedgerService) {
        this.transactionLedgerService = transactionLedgerService;
    }

    @Override
    public void handle(TransactionContext context, TransactionHandlerChain chain) {
        transactionLedgerService.executeInternalTransfer(context);
        chain.next(context);
    }
}
