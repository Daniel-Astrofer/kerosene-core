package source.wallet.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.model.entity.UserDataBase;
import source.ledger.entity.LedgerTransactionHistory;
import source.ledger.repository.LedgerTransactionHistoryRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletCardProfileService Tests")
class WalletCardProfileServiceTest {

    @Mock
    private UserServiceContract userService;

    @Mock
    private LedgerTransactionHistoryRepository historyRepository;

    private WalletCardProfileService service;

    @BeforeEach
    void setUp() {
        service = new WalletCardProfileService(
                userService,
                historyRepository,
                6,
                new BigDecimal("1500"),
                new BigDecimal("3000"),
                new BigDecimal("0.009"),
                new BigDecimal("0.008"),
                new BigDecimal("0.007"));
    }

    @Test
    void returnsBronzeForRecentAccount() {
        when(userService.buscarPorId(1L)).thenReturn(Optional.of(userCreatedAt(LocalDateTime.now().minusMonths(2))));
        when(historyRepository.findMovementHistoryForUser(eq(1L), any(), any())).thenReturn(List.of(
                history("INTERNAL", "CONCLUDED", "5000.00000000")));

        WalletCardProfile profile = service.resolveProfile(1L);

        assertEquals(WalletCardType.BRONZE, profile.cardType());
        assertEquals(new BigDecimal("0.0090"), profile.withdrawalFeeRate());
    }

    @Test
    void returnsWhiteForEligibleAccountWithMovementAboveWhiteThreshold() {
        when(userService.buscarPorId(2L)).thenReturn(Optional.of(userCreatedAt(LocalDateTime.now().minusMonths(8))));
        when(historyRepository.findMovementHistoryForUser(eq(2L), any(), any())).thenReturn(List.of(
                history("INTERNAL", "CONCLUDED", "700.00000000"),
                history("DEPOSIT", "CONCLUDED", "900.00000000"),
                history("PAYMENT_LINK", "PENDING", "900.00000000")));

        WalletCardProfile profile = service.resolveProfile(2L);

        assertEquals(WalletCardType.WHITE, profile.cardType());
        assertEquals(new BigDecimal("0.0080"), profile.depositFeeRate());
        assertEquals(new BigDecimal("1600.00000000"), profile.monthlyMovement());
    }

    @Test
    void returnsBlackForEligibleAccountWithMovementAboveBlackThreshold() {
        when(userService.buscarPorId(3L)).thenReturn(Optional.of(userCreatedAt(LocalDateTime.now().minusMonths(10))));
        when(historyRepository.findMovementHistoryForUser(eq(3L), any(), any())).thenReturn(List.of(
                history("EXTERNAL_ONCHAIN_WITHDRAWAL", "PENDING", "1000.00000000"),
                history("EXTERNAL_DEPOSIT", "CONCLUDED", "1200.00000000"),
                history("MINING_PAYOUT_SETTLEMENT", "COMPLETED", "900.00000000")));

        WalletCardProfile profile = service.resolveProfile(3L);

        assertEquals(WalletCardType.BLACK, profile.cardType());
        assertEquals(new BigDecimal("0.0070"), profile.withdrawalFeeRate());
        assertEquals(new BigDecimal("3100.00000000"), profile.monthlyMovement());
    }

    private UserDataBase userCreatedAt(LocalDateTime createdAt) {
        UserDataBase user = new UserDataBase();
        user.setCreatedAt(createdAt);
        return user;
    }

    private LedgerTransactionHistory history(String type, String status, String amount) {
        LedgerTransactionHistory history = new LedgerTransactionHistory();
        history.setTransactionType(type);
        history.setStatus(status);
        history.setAmount(new BigDecimal(amount));
        history.setCreatedAt(LocalDateTime.now().minusDays(5));
        return history;
    }
}
