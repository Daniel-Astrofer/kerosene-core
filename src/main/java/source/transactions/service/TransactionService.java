package source.transactions.service;

import source.transactions.dto.EstimatedFeeDTO;
import source.transactions.dto.TransactionRequestDTO;
import source.transactions.dto.TransactionResponseDTO;
import source.transactions.dto.UnsignedTransactionDTO;
import source.transactions.dto.WithdrawRequestDTO;

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
    TransactionResponseDTO broadcastTransaction(String rawTxHex, String toAddress, java.math.BigDecimal amount,
            String message, Long userId);

    // Executa um saque on-chain (internally debiting ledger and broadcasting to
    // blockchain)
    TransactionResponseDTO withdraw(Long userId, WithdrawRequestDTO request);
}
