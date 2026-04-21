package source.ledger.application.balance;

public interface LedgerBalanceUpdatePort {

    void publishBalanceUpdated(LedgerBalanceUpdate update);
}
