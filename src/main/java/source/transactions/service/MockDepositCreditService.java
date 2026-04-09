package source.transactions.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.ledger.entity.LedgerTransactionHistory;
import source.ledger.repository.LedgerTransactionHistoryRepository;
import source.ledger.service.LedgerService;
import source.notification.service.NotificationService;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class MockDepositCreditService {

    private static final Logger log = LoggerFactory.getLogger(MockDepositCreditService.class);

    private final WalletService walletService;
    private final LedgerService ledgerService;
    private final LedgerTransactionHistoryRepository historyRepository;
    private final NotificationService notificationService;
    private final boolean enabled;
    private final BigDecimal amountBtc;

    public MockDepositCreditService(WalletService walletService,
            LedgerService ledgerService,
            LedgerTransactionHistoryRepository historyRepository,
            NotificationService notificationService,
            @Value("${transactions.deposit.mock-credit.enabled:false}") boolean enabled,
            @Value("${transactions.deposit.mock-credit.amount-btc:100.00000000}") BigDecimal amountBtc) {
        this.walletService = walletService;
        this.ledgerService = ledgerService;
        this.historyRepository = historyRepository;
        this.notificationService = notificationService;
        this.enabled = enabled;
        this.amountBtc = amountBtc;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Transactional
    public void creditOnDepositAddressRequest(Long userId) {
        if (!enabled) {
            return;
        }

        List<WalletEntity> wallets = walletService.findByUserId(userId);
        if (wallets == null || wallets.isEmpty()) {
            log.warn("[MOCK_DEPOSIT] User {} requested deposit-address but has no wallet to receive the mock credit.", userId);
            return;
        }

        WalletEntity wallet = wallets.get(0);
        ledgerService.updateBalance(wallet.getId(), amountBtc, "MOCK_DEPOSIT_ENDPOINT");

        LedgerTransactionHistory history = new LedgerTransactionHistory();
        history.setId(UUID.randomUUID());
        history.setAmount(amountBtc);
        history.setCreatedAt(LocalDateTime.now());
        history.setContext("Mock deposit credited through /transactions/deposit-address");
        history.setReceiverUserId(userId);
        history.setReceiverIdentifier(wallet.getName());
        history.setSenderIdentifier("MOCK_DEPOSIT_ENDPOINT");
        history.setBlockchainTxid("mock_deposit_" + UUID.randomUUID());
        history.setTransactionType("EXTERNAL_DEPOSIT");
        history.setStatus("CONCLUDED");
        history.setConfirmations(999);
        historyRepository.save(history);

        notificationService.notifyUser(
                userId,
                "Deposito confirmado",
                amountBtc.toPlainString() + " BTC foram creditados via mock local de deposito.");

        log.warn("[MOCK_DEPOSIT] Credited {} BTC to user {} wallet {} via deposit-address endpoint.",
                amountBtc.toPlainString(), userId, wallet.getId());
    }
}
