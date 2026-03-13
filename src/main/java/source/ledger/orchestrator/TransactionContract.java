package source.ledger.orchestrator;

import source.ledger.dto.TransactionDTO;

public interface TransactionContract {

    void processTransaction(TransactionDTO dto);
}
