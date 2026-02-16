package source.transactions.service;

import source.transactions.dto.EstimatedFeeDTO;
import source.transactions.dto.SignedTransactionDTO;
import source.transactions.dto.TransactionRequestDTO;
import source.transactions.dto.TransactionResponseDTO;

import java.math.BigDecimal;

public interface TransactionService {

    TransactionResponseDTO sendTransaction(TransactionRequestDTO request);

    TransactionResponseDTO getStatus(String txid);

    TransactionResponseDTO broadcastSignedTransaction(SignedTransactionDTO signedTx);

    EstimatedFeeDTO estimateFee(BigDecimal amount);

}
