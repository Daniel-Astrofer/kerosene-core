package source.ledger.orchestrator;

import org.springframework.stereotype.Component;
import source.ledger.application.transaction.TransactionProcessingUseCase;
import source.ledger.dto.TransactionDTO;

@Component
public class Transaction implements TransactionContract {

    private final TransactionProcessingUseCase transactionProcessingUseCase;

    public Transaction(TransactionProcessingUseCase transactionProcessingUseCase) {
        this.transactionProcessingUseCase = transactionProcessingUseCase;
    }

    @Override
    public void processTransaction(TransactionDTO dto) {
        transactionProcessingUseCase.process(dto);
    }
}
