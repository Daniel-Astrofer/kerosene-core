package source.transactions.service;

import source.transactions.dto.EstimatedFeeDTO;
import source.transactions.dto.ExternalTransferResponseDTO;
import source.transactions.dto.TransactionRequestDTO;
import source.transactions.dto.TransactionResponseDTO;
import source.transactions.dto.UnsignedTransactionDTO;
import source.transactions.dto.WithdrawRequestDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.transactions.application.transaction.BroadcastTransactionUseCase;
import source.transactions.application.transaction.CheckPendingTransactionsUseCase;
import source.transactions.application.transaction.CreateUnsignedTransactionUseCase;
import source.transactions.application.transaction.EstimateTransactionFeeUseCase;
import source.transactions.application.transaction.GetTransactionStatusUseCase;

import java.math.BigDecimal;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final EstimateTransactionFeeUseCase estimateTransactionFeeUseCase;
    private final CreateUnsignedTransactionUseCase createUnsignedTransactionUseCase;
    private final GetTransactionStatusUseCase getTransactionStatusUseCase;
    private final CheckPendingTransactionsUseCase checkPendingTransactionsUseCase;
    private final BroadcastTransactionUseCase broadcastTransactionUseCase;
    private final ExternalPaymentsService externalPaymentsService;

    public TransactionServiceImpl(
            EstimateTransactionFeeUseCase estimateTransactionFeeUseCase,
            CreateUnsignedTransactionUseCase createUnsignedTransactionUseCase,
            GetTransactionStatusUseCase getTransactionStatusUseCase,
            CheckPendingTransactionsUseCase checkPendingTransactionsUseCase,
            BroadcastTransactionUseCase broadcastTransactionUseCase,
            ExternalPaymentsService externalPaymentsService) {
        this.estimateTransactionFeeUseCase = estimateTransactionFeeUseCase;
        this.createUnsignedTransactionUseCase = createUnsignedTransactionUseCase;
        this.getTransactionStatusUseCase = getTransactionStatusUseCase;
        this.checkPendingTransactionsUseCase = checkPendingTransactionsUseCase;
        this.broadcastTransactionUseCase = broadcastTransactionUseCase;
        this.externalPaymentsService = externalPaymentsService;
    }

    @Override
    public EstimatedFeeDTO estimateFee(BigDecimal amount) {
        return estimateTransactionFeeUseCase.estimate(amount);
    }

    @Override
    public UnsignedTransactionDTO createUnsignedTransaction(TransactionRequestDTO request) {
        return createUnsignedTransactionUseCase.create(request);
    }

    @Override
    public TransactionResponseDTO getTransactionStatus(String txid) {
        return getTransactionStatusUseCase.getStatus(txid);
    }

    @Override
    public void checkPendingTransactions() {
        checkPendingTransactionsUseCase.checkAll();
    }

    @Override
    public TransactionResponseDTO broadcastTransaction(String rawTxHex, String toAddress, BigDecimal amount,
            String message, Long userId) {
        return broadcastTransactionUseCase.broadcast(rawTxHex, toAddress, amount, message, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransactionResponseDTO withdraw(Long userId, WithdrawRequestDTO request) {
        ExternalTransferResponseDTO transfer = externalPaymentsService.sendOnchain(
                userId,
                new source.transactions.dto.OnchainSendRequestDTO(
                        request.getFromWalletName(),
                        request.getToAddress(),
                        request.getAmount(),
                        request.getDescription(),
                        request.getTotpCode(),
                        request.getPasskeyAssertionResponseJSON(),
                        request.getConfirmationPassphrase()));

        return new TransactionResponseDTO(
                transfer.externalReference(),
                transfer.status() != null ? transfer.status().toLowerCase() : "pending",
                transfer.networkFeeBtc().multiply(new BigDecimal("100000000")).longValue(),
                transfer.amountBtc(),
                request.getFromWalletName(),
                request.getToAddress(),
                request.getDescription());
    }
}
