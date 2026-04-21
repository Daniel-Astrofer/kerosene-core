package source.ledger.application.balance;

public interface LedgerIntegrityFailurePort {

    void reportIntegrityFailure(LedgerIntegrityFailure failure);
}
