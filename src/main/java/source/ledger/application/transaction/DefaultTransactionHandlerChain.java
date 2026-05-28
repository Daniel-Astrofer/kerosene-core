package source.ledger.application.transaction;

import java.util.List;

final class DefaultTransactionHandlerChain implements TransactionHandlerChain {

    private final List<TransactionHandler> handlers;
    private int index;

    DefaultTransactionHandlerChain(List<TransactionHandler> handlers) {
        this.handlers = handlers;
    }

    @Override
    public void next(TransactionContext context) {
        if (index >= handlers.size()) {
            return;
        }

        TransactionHandler currentHandler = handlers.get(index++);
        currentHandler.handle(context, this);
    }
}
