package source.transactions.application.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import source.common.infra.logging.LogSanitizer;
import source.transactions.dto.TransactionResponseDTO;
import source.transactions.exception.TransactionExceptions;
import source.transactions.model.PendingTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class BroadcastTransactionUseCase {

    private static final Logger log = LoggerFactory.getLogger(BroadcastTransactionUseCase.class);

    private final TransactionBroadcastPort transactionBroadcastPort;
    private final TransactionPendingPort transactionPendingPort;
    private final TransactionHistoryPort transactionHistoryPort;
    private final TransactionNotificationPort transactionNotificationPort;

    public BroadcastTransactionUseCase(
            TransactionBroadcastPort transactionBroadcastPort,
            TransactionPendingPort transactionPendingPort,
            TransactionHistoryPort transactionHistoryPort,
            TransactionNotificationPort transactionNotificationPort) {
        this.transactionBroadcastPort = transactionBroadcastPort;
        this.transactionPendingPort = transactionPendingPort;
        this.transactionHistoryPort = transactionHistoryPort;
        this.transactionNotificationPort = transactionNotificationPort;
    }

    public TransactionResponseDTO broadcast(
            String rawTxHex,
            String toAddress,
            BigDecimal amount,
            String message,
            Long userId) {
        String txid = transactionBroadcastPort.sendRawTransaction(rawTxHex);
        if (txid == null || txid.isBlank()) {
            log.error("Bitcoin broadcast failed: rawTxHexLength={} toAddressRef={} userId={}",
                    rawTxHex != null ? rawTxHex.length() : 0, LogSanitizer.fingerprint(toAddress), userId);
            throw new TransactionExceptions.TransactionBroadcastFailed(
                    "Falha ao transmitir transação: o gateway blockchain não retornou um txid válido.");
        }

        log.info("Transaction broadcast successful: txRef={} toAddressRef={} amount={} userId={}",
                LogSanitizer.fingerprint(txid), LogSanitizer.fingerprint(toAddress), amount, userId);

        PendingTransaction pending = new PendingTransaction();
        pending.setTxid(txid);
        pending.setStatus("PENDING");
        pending.setRawTxHex(rawTxHex);
        pending.setUserId(userId);
        pending.setToAddress(toAddress);
        if (amount != null) {
            pending.setAmount(amount);
        }
        transactionPendingPort.save(pending);

        try {
            transactionHistoryPort.recordBroadcast(new TransactionHistoryPort.BroadcastRecord(
                    txid,
                    userId,
                    toAddress,
                    amount,
                    message,
                    LocalDateTime.now()));
        } catch (RuntimeException ex) {
            log.warn("Failed to save broadcast history for txRef={}: {}",
                    LogSanitizer.fingerprint(txid), ex.getMessage());
        }

        try {
            transactionNotificationPort.notifySenderBroadcast(userId, amount);
        } catch (RuntimeException ex) {
            log.warn("Failed to notify sender for txRef={}: {}", LogSanitizer.fingerprint(txid), ex.getMessage());
        }

        if (toAddress != null && !toAddress.isBlank()) {
            try {
                transactionNotificationPort.notifyRecipientBroadcast(toAddress, amount, message);
            } catch (RuntimeException ex) {
                log.warn("Failed to notify recipient for txRef={}: {}",
                        LogSanitizer.fingerprint(txid), ex.getMessage());
            }
        }

        return new TransactionResponseDTO(
                txid,
                "pending",
                0L,
                amount != null ? amount : BigDecimal.ZERO,
                null,
                toAddress,
                message);
    }
}
