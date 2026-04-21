package source.ledger.application.transaction;

public interface TransactionHandlerChain {

    void next(TransactionContext context);
}
