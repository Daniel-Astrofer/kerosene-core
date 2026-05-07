package source.bitcoinaccounts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.bitcoinaccounts.model.BitcoinAccountEnums;
import source.bitcoinaccounts.model.LedgerAccountEntity;
import source.bitcoinaccounts.model.LedgerEntryEntity;
import source.bitcoinaccounts.repository.BitcoinLedgerEntryRepository;
import source.bitcoinaccounts.repository.LedgerAccountRepository;

import java.util.Map;
import java.util.UUID;

@Service
public class BitcoinAccountLedgerService {

    private final LedgerAccountRepository ledgerAccountRepository;
    private final BitcoinLedgerEntryRepository ledgerEntryRepository;
    private final BitcoinAccountAuditService auditService;

    public BitcoinAccountLedgerService(
            LedgerAccountRepository ledgerAccountRepository,
            BitcoinLedgerEntryRepository ledgerEntryRepository,
            BitcoinAccountAuditService auditService) {
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.auditService = auditService;
    }

    @Transactional
    public LedgerAccountEntity createLedgerAccount(Long userId, UUID bitcoinAccountId) {
        LedgerAccountEntity ledger = new LedgerAccountEntity();
        ledger.setUserId(userId);
        ledger.setBitcoinAccountId(bitcoinAccountId);
        LedgerAccountEntity saved = ledgerAccountRepository.save(ledger);
        auditService.recordUser(userId, "LEDGER_ACCOUNT_CREATED", "BITCOIN_LEDGER_ACCOUNT",
                saved.getId().toString(), Map.of("bitcoinAccountId", bitcoinAccountId.toString()));
        return saved;
    }

    @Transactional
    public LedgerEntryEntity creditPending(
            UUID ledgerAccountId,
            long amountSats,
            String sourceType,
            String sourceId,
            String idempotencyKey) {
        requirePositive(amountSats);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required for ledger credit.");
        }
        return ledgerEntryRepository.findByIdempotencyKey(idempotencyKey).orElseGet(() -> {
            LedgerAccountEntity account = requireLedgerForUpdate(ledgerAccountId);
            account.setBalancePendingSats(Math.addExact(account.getBalancePendingSats(), amountSats));
            ledgerAccountRepository.save(account);

            LedgerEntryEntity entry = new LedgerEntryEntity();
            entry.setLedgerAccountId(ledgerAccountId);
            entry.setDirection(BitcoinAccountEnums.LedgerDirection.CREDIT);
            entry.setAmountSats(amountSats);
            entry.setStatus(BitcoinAccountEnums.LedgerEntryStatus.PENDING);
            entry.setSourceType(sourceType);
            entry.setSourceId(sourceId);
            entry.setIdempotencyKey(idempotencyKey);
            LedgerEntryEntity saved = ledgerEntryRepository.save(entry);
            auditService.recordUser(account.getUserId(), "LEDGER_CREDIT_PENDING", "BITCOIN_LEDGER_ENTRY",
                    saved.getId().toString(), Map.of("sourceType", sourceType, "amountSats", amountSats));
            return saved;
        });
    }

    @Transactional
    public LedgerEntryEntity makeAvailable(UUID entryId) {
        LedgerEntryEntity entry = ledgerEntryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Ledger entry not found."));
        if (entry.getStatus() == BitcoinAccountEnums.LedgerEntryStatus.AVAILABLE
                || entry.getStatus() == BitcoinAccountEnums.LedgerEntryStatus.FINALIZED) {
            return entry;
        }
        if (entry.getStatus() != BitcoinAccountEnums.LedgerEntryStatus.PENDING
                && entry.getStatus() != BitcoinAccountEnums.LedgerEntryStatus.AUTO_HOLD) {
            throw new IllegalArgumentException("This ledger entry cannot become available from its current state.");
        }

        LedgerAccountEntity account = requireLedgerForUpdate(entry.getLedgerAccountId());
        if (entry.getStatus() == BitcoinAccountEnums.LedgerEntryStatus.PENDING) {
            account.setBalancePendingSats(subtractNonNegative(account.getBalancePendingSats(), entry.getAmountSats()));
        } else {
            account.setBalanceAutoHoldSats(subtractNonNegative(account.getBalanceAutoHoldSats(), entry.getAmountSats()));
        }
        account.setBalanceAvailableSats(Math.addExact(account.getBalanceAvailableSats(), entry.getAmountSats()));
        ledgerAccountRepository.save(account);

        entry.setStatus(BitcoinAccountEnums.LedgerEntryStatus.AVAILABLE);
        LedgerEntryEntity saved = ledgerEntryRepository.save(entry);
        auditService.recordUser(account.getUserId(), "LEDGER_ENTRY_AVAILABLE", "BITCOIN_LEDGER_ENTRY",
                saved.getId().toString(), Map.of("amountSats", saved.getAmountSats()));
        return saved;
    }

    @Transactional
    public LedgerEntryEntity moveToAutoHold(UUID entryId, String reason) {
        LedgerEntryEntity entry = ledgerEntryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Ledger entry not found."));
        if (entry.getStatus() == BitcoinAccountEnums.LedgerEntryStatus.AUTO_HOLD) {
            return entry;
        }
        if (entry.getStatus() != BitcoinAccountEnums.LedgerEntryStatus.PENDING) {
            throw new IllegalArgumentException("Only pending entries can move to automatic hold.");
        }
        LedgerAccountEntity account = requireLedgerForUpdate(entry.getLedgerAccountId());
        account.setBalancePendingSats(subtractNonNegative(account.getBalancePendingSats(), entry.getAmountSats()));
        account.setBalanceAutoHoldSats(Math.addExact(account.getBalanceAutoHoldSats(), entry.getAmountSats()));
        ledgerAccountRepository.save(account);
        entry.setStatus(BitcoinAccountEnums.LedgerEntryStatus.AUTO_HOLD);
        LedgerEntryEntity saved = ledgerEntryRepository.save(entry);
        auditService.recordUser(account.getUserId(), "LEDGER_ENTRY_AUTO_HOLD", "BITCOIN_LEDGER_ENTRY",
                saved.getId().toString(), Map.of("reason", reason != null ? reason : "AUTO_POLICY"));
        return saved;
    }

    @Transactional
    public LedgerEntryEntity moveAvailableToAutoHoldByIdempotencyKey(String idempotencyKey, String reason) {
        LedgerEntryEntity entry = ledgerEntryRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalArgumentException("Ledger entry not found."));
        if (entry.getStatus() == BitcoinAccountEnums.LedgerEntryStatus.AUTO_HOLD) {
            return entry;
        }
        if (entry.getStatus() == BitcoinAccountEnums.LedgerEntryStatus.PENDING) {
            return moveToAutoHold(entry.getId(), reason);
        }
        if (entry.getStatus() != BitcoinAccountEnums.LedgerEntryStatus.AVAILABLE) {
            throw new IllegalArgumentException("Only available or pending entries can move to automatic hold.");
        }
        LedgerAccountEntity account = requireLedgerForUpdate(entry.getLedgerAccountId());
        account.setBalanceAvailableSats(subtractNonNegative(account.getBalanceAvailableSats(), entry.getAmountSats()));
        account.setBalanceAutoHoldSats(Math.addExact(account.getBalanceAutoHoldSats(), entry.getAmountSats()));
        ledgerAccountRepository.save(account);
        entry.setStatus(BitcoinAccountEnums.LedgerEntryStatus.AUTO_HOLD);
        LedgerEntryEntity saved = ledgerEntryRepository.save(entry);
        auditService.recordUser(account.getUserId(), "LEDGER_ENTRY_AUTO_HOLD", "BITCOIN_LEDGER_ENTRY",
                saved.getId().toString(), Map.of("reason", reason != null ? reason : "AUTO_POLICY"));
        return saved;
    }

    @Transactional
    public LedgerEntryEntity requireUserAction(UUID entryId, String reason) {
        LedgerEntryEntity entry = ledgerEntryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Ledger entry not found."));
        if (entry.getStatus() == BitcoinAccountEnums.LedgerEntryStatus.AUTO_HOLD) {
            return entry;
        }
        if (entry.getStatus() == BitcoinAccountEnums.LedgerEntryStatus.PENDING) {
            return moveToAutoHold(entry.getId(), reason != null ? reason : "USER_ACTION_REQUIRED");
        }
        if (entry.getStatus() != BitcoinAccountEnums.LedgerEntryStatus.AVAILABLE) {
            throw new IllegalArgumentException("Only pending or available entries can require user action.");
        }
        LedgerAccountEntity account = requireLedgerForUpdate(entry.getLedgerAccountId());
        account.setBalanceAvailableSats(subtractNonNegative(account.getBalanceAvailableSats(), entry.getAmountSats()));
        account.setBalanceAutoHoldSats(Math.addExact(account.getBalanceAutoHoldSats(), entry.getAmountSats()));
        ledgerAccountRepository.save(account);
        entry.setStatus(BitcoinAccountEnums.LedgerEntryStatus.AUTO_HOLD);
        LedgerEntryEntity saved = ledgerEntryRepository.save(entry);
        auditService.recordUser(account.getUserId(), "LEDGER_ENTRY_USER_ACTION_REQUIRED", "BITCOIN_LEDGER_ENTRY",
                saved.getId().toString(), Map.of("reason", reason != null ? reason : "USER_ACTION_REQUIRED"));
        return saved;
    }

    @Transactional
    public LedgerEntryEntity lockFunds(
            UUID ledgerAccountId,
            long amountSats,
            String sourceType,
            String sourceId,
            String idempotencyKey) {
        requirePositive(amountSats);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required for ledger reservation.");
        }
        return ledgerEntryRepository.findByIdempotencyKey(idempotencyKey).orElseGet(() -> {
            LedgerAccountEntity account = requireLedgerForUpdate(ledgerAccountId);
            account.setBalanceAvailableSats(subtractNonNegative(account.getBalanceAvailableSats(), amountSats));
            account.setBalanceLockedSats(Math.addExact(account.getBalanceLockedSats(), amountSats));
            ledgerAccountRepository.save(account);

            LedgerEntryEntity entry = new LedgerEntryEntity();
            entry.setLedgerAccountId(ledgerAccountId);
            entry.setDirection(BitcoinAccountEnums.LedgerDirection.DEBIT);
            entry.setAmountSats(amountSats);
            entry.setStatus(BitcoinAccountEnums.LedgerEntryStatus.LOCKED);
            entry.setSourceType(sourceType);
            entry.setSourceId(sourceId);
            entry.setIdempotencyKey(idempotencyKey);
            LedgerEntryEntity saved = ledgerEntryRepository.save(entry);
            auditService.recordUser(account.getUserId(), "LEDGER_FUNDS_LOCKED", "BITCOIN_LEDGER_ENTRY",
                    saved.getId().toString(), Map.of("sourceType", sourceType, "amountSats", amountSats));
            return saved;
        });
    }

    @Transactional
    public LedgerEntryEntity debitReserved(UUID entryId) {
        LedgerEntryEntity entry = ledgerEntryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Ledger entry not found."));
        if (entry.getStatus() == BitcoinAccountEnums.LedgerEntryStatus.FINALIZED) {
            return entry;
        }
        if (entry.getDirection() != BitcoinAccountEnums.LedgerDirection.DEBIT
                || entry.getStatus() != BitcoinAccountEnums.LedgerEntryStatus.LOCKED) {
            throw new IllegalArgumentException("Only locked debit entries can be finalized.");
        }
        LedgerAccountEntity account = requireLedgerForUpdate(entry.getLedgerAccountId());
        account.setBalanceLockedSats(subtractNonNegative(account.getBalanceLockedSats(), entry.getAmountSats()));
        ledgerAccountRepository.save(account);
        entry.setStatus(BitcoinAccountEnums.LedgerEntryStatus.FINALIZED);
        LedgerEntryEntity saved = ledgerEntryRepository.save(entry);
        auditService.recordUser(account.getUserId(), "LEDGER_DEBIT_FINALIZED", "BITCOIN_LEDGER_ENTRY",
                saved.getId().toString(), Map.of("amountSats", saved.getAmountSats()));
        return saved;
    }

    @Transactional
    public LedgerEntryEntity reverseEntry(UUID entryId) {
        LedgerEntryEntity entry = ledgerEntryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Ledger entry not found."));
        if (entry.getStatus() == BitcoinAccountEnums.LedgerEntryStatus.REVERSED) {
            return entry;
        }
        if (entry.getStatus() == BitcoinAccountEnums.LedgerEntryStatus.FINALIZED) {
            throw new IllegalArgumentException("Finalized ledger entries cannot be reversed by this operation.");
        }
        LedgerAccountEntity account = requireLedgerForUpdate(entry.getLedgerAccountId());
        if (entry.getDirection() == BitcoinAccountEnums.LedgerDirection.CREDIT) {
            reverseCredit(entry, account);
        } else {
            reverseDebit(entry, account);
        }
        ledgerAccountRepository.save(account);
        entry.setStatus(BitcoinAccountEnums.LedgerEntryStatus.REVERSED);
        LedgerEntryEntity saved = ledgerEntryRepository.save(entry);
        auditService.recordUser(account.getUserId(), "LEDGER_ENTRY_REVERSED", "BITCOIN_LEDGER_ENTRY",
                saved.getId().toString(), Map.of("amountSats", saved.getAmountSats()));
        return saved;
    }

    @Transactional(readOnly = true)
    public LedgerAccountEntity getBalances(UUID ledgerAccountId) {
        return ledgerAccountRepository.findById(ledgerAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Ledger account not found."));
    }

    @Transactional(readOnly = true)
    public boolean hasEntryForIdempotencyKey(String idempotencyKey) {
        return idempotencyKey != null
                && !idempotencyKey.isBlank()
                && ledgerEntryRepository.findByIdempotencyKey(idempotencyKey).isPresent();
    }

    private LedgerAccountEntity requireLedgerForUpdate(UUID ledgerAccountId) {
        return ledgerAccountRepository.findByIdForUpdate(ledgerAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Ledger account not found."));
    }

    private void requirePositive(long amountSats) {
        if (amountSats <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
    }

    private long subtractNonNegative(long current, long amount) {
        long result = current - amount;
        if (result < 0) {
            throw new IllegalStateException("Ledger invariant failed: balance bucket would become negative.");
        }
        return result;
    }

    private void reverseCredit(LedgerEntryEntity entry, LedgerAccountEntity account) {
        switch (entry.getStatus()) {
            case PENDING -> account.setBalancePendingSats(
                    subtractNonNegative(account.getBalancePendingSats(), entry.getAmountSats()));
            case AVAILABLE -> account.setBalanceAvailableSats(
                    subtractNonNegative(account.getBalanceAvailableSats(), entry.getAmountSats()));
            case AUTO_HOLD -> account.setBalanceAutoHoldSats(
                    subtractNonNegative(account.getBalanceAutoHoldSats(), entry.getAmountSats()));
            default -> throw new IllegalArgumentException("This credit entry cannot be reversed from its current state.");
        }
    }

    private void reverseDebit(LedgerEntryEntity entry, LedgerAccountEntity account) {
        if (entry.getStatus() != BitcoinAccountEnums.LedgerEntryStatus.LOCKED) {
            throw new IllegalArgumentException("Only locked debit entries can be reversed.");
        }
        account.setBalanceLockedSats(subtractNonNegative(account.getBalanceLockedSats(), entry.getAmountSats()));
        account.setBalanceAvailableSats(Math.addExact(account.getBalanceAvailableSats(), entry.getAmountSats()));
    }
}
