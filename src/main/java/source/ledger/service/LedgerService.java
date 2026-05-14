package source.ledger.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import source.common.validation.FinancialAmountValidator;
import source.ledger.application.balance.LedgerBalanceConsensusPort;
import source.ledger.application.balance.LedgerBalanceUpdate;
import source.ledger.application.balance.LedgerBalanceUpdatePort;
import source.ledger.application.balance.LedgerHashService;
import source.ledger.application.balance.LedgerIntegrityService;
import source.ledger.dto.LedgerDTO;
import source.ledger.entity.LedgerEntity;
import source.ledger.exceptions.LedgerExceptions;
import source.ledger.repository.LedgerRepository;
import source.treasury.service.FinancialAuditTrailService;
import source.wallet.model.WalletEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LedgerService implements LedgerContract {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final LedgerRepository ledgerRepository;
    private final LedgerHashService ledgerHashService;
    private final LedgerIntegrityService ledgerIntegrityService;
    private final LedgerBalanceConsensusPort balanceConsensusPort;
    private final LedgerBalanceUpdatePort balanceUpdatePort;
    private final FinancialAuditTrailService auditTrailService;

    public LedgerService(LedgerRepository ledgerRepository,
            LedgerHashService ledgerHashService,
            LedgerIntegrityService ledgerIntegrityService,
            LedgerBalanceConsensusPort balanceConsensusPort,
            LedgerBalanceUpdatePort balanceUpdatePort,
            FinancialAuditTrailService auditTrailService) {
        this.ledgerRepository = ledgerRepository;
        this.ledgerHashService = ledgerHashService;
        this.ledgerIntegrityService = ledgerIntegrityService;
        this.balanceConsensusPort = balanceConsensusPort;
        this.balanceUpdatePort = balanceUpdatePort;
        this.auditTrailService = auditTrailService;
    }

    @Override
    @Transactional
    public LedgerEntity createLedger(WalletEntity wallet, String context) {
        if (ledgerRepository.existsByWalletId(wallet.getId())) {
            throw new LedgerExceptions.LedgerAlreadyExistsException("Ledger already exists for this wallet");
        }

        LedgerEntity ledger = new LedgerEntity(wallet, context);
        ledger.setLastHash(ledgerHashService.generateInitialHash(wallet.getId()));
        ledger.setBalanceSignature(ledgerHashService.generateBalanceSignature(ledger));

        return ledgerRepository.save(ledger);
    }

    @Override
    public LedgerEntity findByWalletId(Long walletId) {
        LedgerEntity ledger = ledgerRepository.findByWalletId(walletId)
                .orElseThrow(() -> new LedgerExceptions.LedgerNotFoundException(
                        "Ledger not found for wallet ID: " + walletId));

        ledgerIntegrityService.verifyBalanceIntegrity(ledger);
        return ledger;
    }

    @Override
    public boolean existsByWalletId(Long walletId) {
        return ledgerRepository.existsByWalletId(walletId);
    }

    @Override
    public List<LedgerEntity> findByUserId(Long userId) {
        // Issue 2.4: Avoid N+1 — verifyBalanceIntegrity() is called per-ledger.
        // Fetch all in one query and verify in batch to minimise DB round trips.
        List<LedgerEntity> ledgers = ledgerRepository.findByWalletUserId(userId);
        ledgers.forEach(ledgerIntegrityService::verifyBalanceIntegrity);
        return ledgers;
    }

    @Override
    @Transactional
    public LedgerEntity updateBalance(Long walletId, BigDecimal amount, String context) {
        FinancialAmountValidator.requireNonZeroBtcDelta(amount, "amount");
        LedgerEntity ledger = ledgerRepository.findByWalletIdForUpdate(walletId)
                .orElseThrow(() -> new LedgerExceptions.LedgerNotFoundException(
                        "Ledger not found for wallet ID: " + walletId));

        ledgerIntegrityService.verifyBalanceIntegrity(ledger);

        // Validate sufficient balance for debit operations
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal newBalance = ledger.getBalance().add(amount);
            if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                throw new LedgerExceptions.InsufficientBalanceException("Insufficient balance for this operation");
            }
        }

        // Snapshot BEFORE any mutation — used for compensating revert (Bug 3)
        LedgerSnapshot snapshot = LedgerSnapshot.from(ledger);

        ledger.updateBalance(amount);
        ledger.incrementNonce();
        ledger.setContext(context);
        String finalHash = ledgerHashService.generateHash(ledger);
        ledger.setLastHash(finalHash);
        ledger.setBalanceSignature(ledgerHashService.generateBalanceSignature(ledger));

        try {
            balanceConsensusPort.requireConsensus(finalHash);
        } catch (RuntimeException exception) {
            snapshot.restoreTo(ledger);
            throw exception;
        }

        LedgerEntity saved = ledgerRepository.save(ledger);
        Long userId = saved.getWallet() != null && saved.getWallet().getUser() != null
                ? saved.getWallet().getUser().getId()
                : null;
        LedgerBalanceUpdate balanceUpdate = new LedgerBalanceUpdate(
                ledger.getWallet().getId(),
                ledger.getWallet().getName(),
                ledger.getWallet().getUser().getId(),
                ledger.getBalance(),
                amount,
                context);
        afterCommit(() -> {
            auditTrailService.recordBestEffort(
                    "LEDGER_BALANCE_MUTATION",
                    "LEDGER",
                    saved.getId() != null ? saved.getId().toString() : String.valueOf(walletId),
                    userId,
                    context,
                    Map.of(
                            "walletId", walletId,
                            "amount", amount.toPlainString(),
                            "balance", saved.getBalance().toPlainString(),
                            "nonce", saved.getNonce() != null ? saved.getNonce() : 0));
            balanceUpdatePort.publishBalanceUpdated(balanceUpdate);
        });

        return saved;
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private record LedgerSnapshot(
            BigDecimal balance,
            Integer nonce,
            String lastHash,
            String balanceSignature,
            String context) {

        static LedgerSnapshot from(LedgerEntity ledger) {
            return new LedgerSnapshot(
                    ledger.getBalance(),
                    ledger.getNonce(),
                    ledger.getLastHash(),
                    ledger.getBalanceSignature(),
                    ledger.getContext());
        }

        void restoreTo(LedgerEntity ledger) {
            ledger.setBalance(balance);
            ledger.setNonce(nonce);
            ledger.setLastHash(lastHash);
            ledger.setBalanceSignature(balanceSignature);
            ledger.setContext(context);
        }
    }

    @Override
    public BigDecimal getBalance(Long walletId) {
        LedgerEntity ledger = findByWalletId(walletId);
        return ledger.getBalance();
    }

    @Override
    @Transactional
    public void deleteLedger(Long walletId) {
        if (!ledgerRepository.existsByWalletId(walletId)) {
            throw new LedgerExceptions.LedgerNotFoundException("Ledger not found for wallet ID: " + walletId);
        }
        ledgerRepository.deleteByWalletId(walletId);
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerDTO toDTO(LedgerEntity ledger) {
        LedgerDTO dto = new LedgerDTO();
        dto.setId(ledger.getId());
        dto.setWalletId(ledger.getWallet().getId());
        dto.setWalletName(ledger.getWallet().getName());
        dto.setBalance(ledger.getBalance());
        dto.setNonce(ledger.getNonce());
        dto.setLastHash(ledger.getLastHash());
        dto.setContext(ledger.getContext());
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerDTO> toDTOList(List<LedgerEntity> ledgers) {
        return ledgers.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public void validateWalletOwnership(WalletEntity wallet, Long userId) {
        if (wallet == null || wallet.getUser() == null || !wallet.getUser().getId().equals(userId)) {
            throw new RuntimeException("Carteira não encontrada ou não pertence ao usuário logado.");
        }
    }

    @Override
    @Transactional
    public boolean validateUserLedgerIntegrity(Long userId) {
        if (userId == null) {
            throw new SecurityException("User id is required for ledger integrity validation.");
        }

        List<LedgerEntity> ledgers = ledgerRepository.findByWalletUserId(userId);
        if (ledgers.isEmpty()) {
            // No ledger rows exist for this user, so there is no balance signature to verify.
            log.warn("[LedgerService] No ledgers found while validating integrity for user {}", userId);
            return true;
        }

        try {
            ledgers.forEach(ledgerIntegrityService::verifyBalanceIntegrity);
            return true;
        } catch (RuntimeException e) {
            throw new SecurityException("Ledger integrity validation failed for user " + userId, e);
        }
    }
}
