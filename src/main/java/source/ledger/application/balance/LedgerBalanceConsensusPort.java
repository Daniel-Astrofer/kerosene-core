package source.ledger.application.balance;

public interface LedgerBalanceConsensusPort {

    void requireConsensus(String ledgerHash);
}
