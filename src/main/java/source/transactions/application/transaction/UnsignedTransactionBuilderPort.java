package source.transactions.application.transaction;

import source.transactions.dto.TransactionRequestDTO;
import source.transactions.dto.UnsignedTransactionDTO;

public interface UnsignedTransactionBuilderPort {

    UnsignedTransactionDTO build(TransactionRequestDTO request);
}
