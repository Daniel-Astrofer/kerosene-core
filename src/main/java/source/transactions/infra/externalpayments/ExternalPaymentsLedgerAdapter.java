package source.transactions.infra.externalpayments;

import org.springframework.stereotype.Component;
import source.ledger.entity.LedgerEntry;
import source.ledger.entity.LedgerTransactionHistory;
import source.ledger.exceptions.LedgerExceptions;
import source.ledger.repository.LedgerEntryRepository;
import source.ledger.repository.LedgerTransactionHistoryRepository;
import source.ledger.service.LedgerService;
import source.transactions.application.externalpayments.ExternalPaymentsLedgerPort;
import source.transactions.application.externalpayments.ExternalPaymentsMath;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class ExternalPaymentsLedgerAdapter implements ExternalPaymentsLedgerPort {

    private final LedgerService ledgerService;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerTransactionHistoryRepository historyRepository;
    private final ExternalPaymentsMath externalPaymentsMath;

    public ExternalPaymentsLedgerAdapter(
            LedgerService ledgerService,
            LedgerEntryRepository ledgerEntryRepository,
            LedgerTransactionHistoryRepository historyRepository,
            ExternalPaymentsMath externalPaymentsMath) {
        this.ledgerService = ledgerService;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.historyRepository = historyRepository;
        this.externalPaymentsMath = externalPaymentsMath;
    }

    @Override
    public void ensureBalance(Long walletId, BigDecimal requiredAmount) {
        BigDecimal current = ledgerService.getBalance(walletId);
        if (current.compareTo(requiredAmount) < 0) {
            throw new LedgerExceptions.InsufficientBalanceException(
                    "Insufficient internal balance to cover the amount plus external fees.");
        }
    }

    @Override
    public void updateBalance(Long walletId, BigDecimal amount, String context) {
        ledgerService.updateBalance(walletId, amount, context);
    }

    @Override
    public void recordPlatformFee(UUID transferId, Long userId, BigDecimal totalDebited, BigDecimal platformFee) {
        LedgerEntry entry = new LedgerEntry(
                transferId,
                String.valueOf(userId),
                externalPaymentsMath.normalizeBtc(totalDebited).negate(),
                externalPaymentsMath.normalizeBtc(platformFee),
                "PENDING");
        ledgerEntryRepository.save(entry);
    }

    @Override
    public void recordHistory(HistoryRecord historyRecord) {
        LedgerTransactionHistory history = new LedgerTransactionHistory();
        history.setId(UUID.randomUUID());
        history.setSenderUserId(historyRecord.userId());
        history.setSenderIdentifier(historyRecord.senderIdentifier());
        history.setReceiverIdentifier(historyRecord.receiverIdentifier() != null
                ? historyRecord.receiverIdentifier()
                : "EXTERNAL");
        history.setTransactionType(historyRecord.transactionType());
        history.setAmount(externalPaymentsMath.normalizeBtc(historyRecord.amount()));
        history.setNetworkFee(externalPaymentsMath.normalizeBtc(historyRecord.networkFee()));
        history.setStatus(historyRecord.status() != null ? historyRecord.status() : "PENDING");
        history.setBlockchainTxid(historyRecord.blockchainTxid());
        history.setContext(historyRecord.context());
        history.setCreatedAt(historyRecord.createdAt() != null ? historyRecord.createdAt() : LocalDateTime.now());
        historyRepository.save(history);
    }
}
