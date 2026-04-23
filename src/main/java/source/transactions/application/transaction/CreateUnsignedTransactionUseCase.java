package source.transactions.application.transaction;

import org.springframework.stereotype.Service;
import source.transactions.dto.TransactionRequestDTO;
import source.transactions.dto.UnsignedTransactionDTO;

import java.time.LocalDateTime;

@Service
public class CreateUnsignedTransactionUseCase {

    private final UnsignedTransactionBuilderPort unsignedTransactionBuilderPort;
    private final TransactionHistoryPort transactionHistoryPort;

    public CreateUnsignedTransactionUseCase(
            UnsignedTransactionBuilderPort unsignedTransactionBuilderPort,
            TransactionHistoryPort transactionHistoryPort) {
        this.unsignedTransactionBuilderPort = unsignedTransactionBuilderPort;
        this.transactionHistoryPort = transactionHistoryPort;
    }

    public UnsignedTransactionDTO create(TransactionRequestDTO request) {
        UnsignedTransactionDTO unsignedTx = unsignedTransactionBuilderPort.build(request);

        transactionHistoryPort.recordUnsignedTransaction(new TransactionHistoryPort.UnsignedTransactionRecord(
                request.getFromAddress(),
                request.getToAddress(),
                request.getAmount(),
                LocalDateTime.now()));

        return unsignedTx;
    }
}
