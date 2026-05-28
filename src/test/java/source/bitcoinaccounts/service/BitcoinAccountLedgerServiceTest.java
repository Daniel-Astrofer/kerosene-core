package source.bitcoinaccounts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import source.bitcoinaccounts.model.BitcoinAccountEnums;
import source.bitcoinaccounts.model.LedgerAccountEntity;
import source.bitcoinaccounts.model.LedgerEntryEntity;
import source.bitcoinaccounts.repository.BitcoinLedgerEntryRepository;
import source.bitcoinaccounts.repository.LedgerAccountRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BitcoinAccountLedgerServiceTest {

    private LedgerAccountRepository ledgerAccountRepository;
    private BitcoinLedgerEntryRepository ledgerEntryRepository;
    private BitcoinAccountLedgerService service;

    @BeforeEach
    void setUp() {
        ledgerAccountRepository = mock(LedgerAccountRepository.class);
        ledgerEntryRepository = mock(BitcoinLedgerEntryRepository.class);
        BitcoinAccountAuditService auditService = mock(BitcoinAccountAuditService.class);
        service = new BitcoinAccountLedgerService(ledgerAccountRepository, ledgerEntryRepository, auditService);
        when(ledgerAccountRepository.save(any(LedgerAccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerEntryRepository.save(any(LedgerEntryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void lockFundsAndDebitReservedMoveOnlyLockedBucket() {
        LedgerAccountEntity account = ledgerAccount(50_000L, 0L, 0L, 0L);
        when(ledgerEntryRepository.findByIdempotencyKey("withdraw:1")).thenReturn(Optional.empty());
        when(ledgerAccountRepository.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));

        LedgerEntryEntity reserved = service.lockFunds(
                account.getId(),
                20_000L,
                "WITHDRAWAL",
                "withdrawal-1",
                "withdraw:1");

        assertEquals(30_000L, account.getBalanceAvailableSats());
        assertEquals(20_000L, account.getBalanceLockedSats());
        assertEquals(BitcoinAccountEnums.LedgerDirection.DEBIT, reserved.getDirection());
        assertEquals(BitcoinAccountEnums.LedgerEntryStatus.LOCKED, reserved.getStatus());

        when(ledgerEntryRepository.findById(reserved.getId())).thenReturn(Optional.of(reserved));

        service.debitReserved(reserved.getId());

        assertEquals(30_000L, account.getBalanceAvailableSats());
        assertEquals(0L, account.getBalanceLockedSats());
        assertEquals(BitcoinAccountEnums.LedgerEntryStatus.FINALIZED, reserved.getStatus());
    }

    @Test
    void reverseLockedDebitRestoresAvailableFunds() {
        LedgerAccountEntity account = ledgerAccount(30_000L, 0L, 20_000L, 0L);
        LedgerEntryEntity entry = ledgerEntry(
                account.getId(),
                BitcoinAccountEnums.LedgerDirection.DEBIT,
                BitcoinAccountEnums.LedgerEntryStatus.LOCKED,
                20_000L);
        when(ledgerEntryRepository.findById(entry.getId())).thenReturn(Optional.of(entry));
        when(ledgerAccountRepository.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));

        service.reverseEntry(entry.getId());

        assertEquals(50_000L, account.getBalanceAvailableSats());
        assertEquals(0L, account.getBalanceLockedSats());
        assertEquals(BitcoinAccountEnums.LedgerEntryStatus.REVERSED, entry.getStatus());
    }

    @Test
    void requireUserActionMovesAvailableCreditToAutoHold() {
        LedgerAccountEntity account = ledgerAccount(12_000L, 0L, 0L, 0L);
        LedgerEntryEntity entry = ledgerEntry(
                account.getId(),
                BitcoinAccountEnums.LedgerDirection.CREDIT,
                BitcoinAccountEnums.LedgerEntryStatus.AVAILABLE,
                12_000L);
        when(ledgerEntryRepository.findById(entry.getId())).thenReturn(Optional.of(entry));
        when(ledgerAccountRepository.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));

        service.requireUserAction(entry.getId(), "POLICY_CONFIRMATION");

        assertEquals(0L, account.getBalanceAvailableSats());
        assertEquals(12_000L, account.getBalanceAutoHoldSats());
        assertEquals(BitcoinAccountEnums.LedgerEntryStatus.AUTO_HOLD, entry.getStatus());
    }

    @Test
    void lockFundsRejectsNegativeAvailableBucket() {
        LedgerAccountEntity account = ledgerAccount(1_000L, 0L, 0L, 0L);
        when(ledgerEntryRepository.findByIdempotencyKey("withdraw:too-large")).thenReturn(Optional.empty());
        when(ledgerAccountRepository.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));

        assertThrows(IllegalStateException.class, () -> service.lockFunds(
                account.getId(),
                2_000L,
                "WITHDRAWAL",
                "withdrawal-2",
                "withdraw:too-large"));
    }

    private LedgerAccountEntity ledgerAccount(long available, long pending, long locked, long autoHold) {
        LedgerAccountEntity account = new LedgerAccountEntity();
        account.setUserId(42L);
        account.setBitcoinAccountId(UUID.randomUUID());
        account.setBalanceAvailableSats(available);
        account.setBalancePendingSats(pending);
        account.setBalanceLockedSats(locked);
        account.setBalanceAutoHoldSats(autoHold);
        return account;
    }

    private LedgerEntryEntity ledgerEntry(
            UUID ledgerAccountId,
            BitcoinAccountEnums.LedgerDirection direction,
            BitcoinAccountEnums.LedgerEntryStatus status,
            long amountSats) {
        LedgerEntryEntity entry = new LedgerEntryEntity();
        entry.setLedgerAccountId(ledgerAccountId);
        entry.setDirection(direction);
        entry.setStatus(status);
        entry.setAmountSats(amountSats);
        entry.setSourceType("TEST");
        entry.setSourceId("test-source");
        entry.setIdempotencyKey(UUID.randomUUID().toString());
        return entry;
    }
}
