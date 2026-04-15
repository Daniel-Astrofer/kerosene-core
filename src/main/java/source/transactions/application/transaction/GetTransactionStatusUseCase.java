package source.transactions.application.transaction;

import org.springframework.stereotype.Service;
import source.transactions.dto.TransactionResponseDTO;
import source.transactions.model.PendingTransaction;

@Service
public class GetTransactionStatusUseCase {

    private final TransactionPendingPort transactionPendingPort;

    public GetTransactionStatusUseCase(TransactionPendingPort transactionPendingPort) {
        this.transactionPendingPort = transactionPendingPort;
    }

    public TransactionResponseDTO getStatus(String txid) {
        PendingTransaction pending = transactionPendingPort.findByTxid(txid).orElse(null);
        if (pending != null) {
            return new TransactionResponseDTO(
                    txid,
                    pending.getStatus().toLowerCase(),
                    pending.getFeeSatoshis(),
                    pending.getAmount());
        }
        return new TransactionResponseDTO(txid, "confirmed", 0L);
    }
}
