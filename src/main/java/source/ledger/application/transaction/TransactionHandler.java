package source.ledger.application.transaction;

public interface TransactionHandler {

    void handle(TransactionContext context, TransactionHandlerChain chain);
}
