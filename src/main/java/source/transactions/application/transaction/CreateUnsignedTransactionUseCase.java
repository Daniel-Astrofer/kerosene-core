package source.transactions.application.transaction;

import org.springframework.stereotype.Service;
import source.transactions.dto.TransactionRequestDTO;
import source.transactions.dto.UnsignedTransactionDTO;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CreateUnsignedTransactionUseCase {

    static final String RAW_TX_HEX_PLACEHOLDER = "RAW_TX_HEX_PLACEHOLDER";

    private final TransactionHistoryPort transactionHistoryPort;

    public CreateUnsignedTransactionUseCase(TransactionHistoryPort transactionHistoryPort) {
        this.transactionHistoryPort = transactionHistoryPort;
    }

    public UnsignedTransactionDTO create(TransactionRequestDTO request) {
        UnsignedTransactionDTO unsignedTx = new UnsignedTransactionDTO();
        unsignedTx.setTxId("temp-" + UUID.randomUUID());
        unsignedTx.setFromAddress(request.getFromAddress());
        unsignedTx.setToAddress(request.getToAddress());
        unsignedTx.setTotalAmount(request.getAmount());
        unsignedTx.setFee(request.getFeeSatoshis());
        unsignedTx.setRawTxHex(RAW_TX_HEX_PLACEHOLDER);

        transactionHistoryPort.recordUnsignedTransaction(new TransactionHistoryPort.UnsignedTransactionRecord(
                request.getFromAddress(),
                request.getToAddress(),
                request.getAmount(),
                LocalDateTime.now()));

        return unsignedTx;
    }
}
