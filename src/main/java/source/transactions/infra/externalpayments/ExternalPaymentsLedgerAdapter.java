package source.transactions.infra.externalpayments;

import org.springframework.stereotype.Component;
import source.common.validation.FinancialAmountValidator;
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

    private static final String PLATFORM_FEE_OWNER = "PLATFORM";

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
        requireWalletId(walletId);
        BigDecimal required = requirePositiveBtc(requiredAmount, "requiredAmount");
        BigDecimal current = nonNegativeBtc(ledgerService.getBalance(walletId), "currentBalance");
        if (current.compareTo(required) < 0) {
            throw new LedgerExceptions.InsufficientBalanceException(
                    "Insufficient internal balance to cover the amount plus external fees.");
        }
    }

    @Override
    public void updateBalance(Long walletId, BigDecimal amount, String context) {
        requireWalletId(walletId);
        FinancialAmountValidator.requireNonZeroBtcDelta(amount, "amount");
        ledgerService.updateBalance(walletId, externalPaymentsMath.normalizeBtc(amount), context);
    }

    @Override
    public void recordPlatformFee(UUID transferId, Long userId, BigDecimal totalDebited, BigDecimal platformFee) {
        if (transferId == null) {
            throw new IllegalArgumentException("transferId is required for platform fee ledger entry.");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId is required for platform fee ledger entry.");
        }
        BigDecimal normalizedTotalDebited = requirePositiveBtc(totalDebited, "totalDebited");
        BigDecimal normalizedPlatformFee = nonNegativeBtc(platformFee, "platformFee");
        if (normalizedPlatformFee.compareTo(normalizedTotalDebited) > 0) {
            throw new IllegalArgumentException("platformFee cannot exceed totalDebited.");
        }

        LedgerEntry entry = new LedgerEntry(
                transferId,
                PLATFORM_FEE_OWNER,
                externalPaymentsMath.normalizeBtc(BigDecimal.ZERO),
                normalizedPlatformFee,
                "PENDING");
        ledgerEntryRepository.save(entry);
    }

    @Override
    public void recordHistory(HistoryRecord historyRecord) {
        if (historyRecord == null) {
            throw new IllegalArgumentException("historyRecord is required.");
        }
        BigDecimal amount = requirePositiveBtc(historyRecord.amount(), "history amount");
        BigDecimal networkFee = nonNegativeBtc(historyRecord.networkFee(), "history networkFee");

        LedgerTransactionHistory history = new LedgerTransactionHistory();
        history.setId(UUID.randomUUID());
        history.setSenderUserId(historyRecord.userId());
        history.setSenderIdentifier(historyRecord.senderIdentifier());
        history.setReceiverIdentifier(historyRecord.receiverIdentifier() != null
                ? historyRecord.receiverIdentifier()
                : "EXTERNAL");
        history.setTransactionType(historyRecord.transactionType());
        history.setAmount(amount);
        history.setNetworkFee(networkFee);
        history.setStatus(historyRecord.status() != null ? historyRecord.status() : "PENDING");
        history.setBlockchainTxid(historyRecord.blockchainTxid());
        history.setContext(historyRecord.context());
        history.setCreatedAt(historyRecord.createdAt() != null ? historyRecord.createdAt() : LocalDateTime.now());
        historyRepository.save(history);
    }

    private void requireWalletId(Long walletId) {
        if (walletId == null) {
            throw new IllegalArgumentException("walletId is required.");
        }
    }

    private BigDecimal requirePositiveBtc(BigDecimal value, String fieldName) {
        FinancialAmountValidator.requirePositiveBtc(value, fieldName);
        return externalPaymentsMath.normalizeBtc(value);
    }

    private BigDecimal nonNegativeBtc(BigDecimal value, String fieldName) {
        if (value == null) {
            return externalPaymentsMath.normalizeBtc(BigDecimal.ZERO);
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative.");
        }
        FinancialAmountValidator.requireBtcPrecision(value, fieldName);
        return externalPaymentsMath.normalizeBtc(value);
    }
}
