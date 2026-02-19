package source.transactions.service;

import source.transactions.dto.EstimatedFeeDTO;
import source.transactions.dto.TransactionRequestDTO;
import source.transactions.dto.TransactionResponseDTO;
import source.transactions.dto.UnsignedTransactionDTO;

import java.math.BigDecimal;

public interface TransactionService {

    // Cria transação não assinada para o cliente assinar
    UnsignedTransactionDTO createUnsignedTransaction(TransactionRequestDTO request);

    // Consulta status de uma transação pendente (polling da blockchain)
    TransactionResponseDTO getTransactionStatus(String txid);

    // Estima taxas de transação
    EstimatedFeeDTO estimateFee(BigDecimal amount);

    // Verifica e atualiza status de transações pendentes (chamado por scheduler)
    void checkPendingTransactions();

    // Transmite uma transação assinada para a rede (ou mock)
    TransactionResponseDTO broadcastTransaction(String rawTxHex);
}
