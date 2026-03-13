package source.ledger.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.auth.application.service.cripto.contracts.Hasher;
import source.ledger.dto.LedgerDTO;
import source.ledger.entity.LedgerEntity;
import source.ledger.exceptions.LedgerExceptions;
import source.ledger.repository.LedgerRepository;
import source.ledger.event.BalanceEventPublisher;
import source.ledger.repository.LedgerTransactionHistoryRepository;
import source.ledger.sync.QuorumSyncService;
import source.ledger.entity.LedgerTransactionHistory;
import source.wallet.model.WalletEntity;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.model.entity.UserDataBase;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LedgerService implements LedgerContract {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final LedgerRepository ledgerRepository;
    private final Hasher hash;
    private final BalanceEventPublisher balanceEventPublisher;
    private final LedgerTransactionHistoryRepository historyRepository;
    private final UserServiceContract userService;
    private final QuorumSyncService quorumSyncService;

    public LedgerService(LedgerRepository ledgerRepository,
            @Qualifier("SHAHasher") Hasher hash,
            BalanceEventPublisher balanceEventPublisher,
            LedgerTransactionHistoryRepository historyRepository,
            UserServiceContract userService,
            QuorumSyncService quorumSyncService) {
        this.ledgerRepository = ledgerRepository;
        this.hash = hash;
        this.balanceEventPublisher = balanceEventPublisher;
        this.historyRepository = historyRepository;
        this.userService = userService;
        this.quorumSyncService = quorumSyncService;
    }

    @Override
    @Transactional
    public LedgerEntity createLedger(WalletEntity wallet, String context) {
        if (ledgerRepository.existsByWalletId(wallet.getId())) {
            throw new LedgerExceptions.LedgerAlreadyExistsException("Ledger already exists for this wallet");
        }

        LedgerEntity ledger = new LedgerEntity(wallet, context);
        ledger.setLastHash(generateInitialHash(wallet.getId()));
        ledger.setBalanceSignature(generateBalanceSignature(ledger));

        return ledgerRepository.save(ledger);
    }

    @Override
    public LedgerEntity findByWalletId(Long walletId) {
        LedgerEntity ledger = ledgerRepository.findByWalletId(walletId)
                .orElseThrow(() -> new LedgerExceptions.LedgerNotFoundException(
                        "Ledger not found for wallet ID: " + walletId));

        verifyBalanceIntegrity(ledger);
        return ledger;
    }

    @Override
    public List<LedgerEntity> findByUserId(Long userId) {
        // Issue 2.4: Avoid N+1 — verifyBalanceIntegrity() is called per-ledger.
        // Fetch all in one query and verify in batch to minimise DB round trips.
        List<LedgerEntity> ledgers = ledgerRepository.findByWalletUserId(userId);
        ledgers.forEach(this::verifyBalanceIntegrity);
        return ledgers;
    }

    @Override
    @Transactional
    public LedgerEntity updateBalance(Long walletId, BigDecimal amount, String context) {
        LedgerEntity ledger = ledgerRepository.findByWalletIdForUpdate(walletId)
                .orElseThrow(() -> new LedgerExceptions.LedgerNotFoundException(
                        "Ledger not found for wallet ID: " + walletId));

        verifyBalanceIntegrity(ledger);

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
        String finalHash = generateHash(ledger);
        ledger.setLastHash(finalHash);
        ledger.setBalanceSignature(generateBalanceSignature(ledger));

        // ─── Two-Phase Quorum (Issue 1.4) ──────────────────────────────────────
        // proposeTransactionToQuorum runs PREPARE + COMMIT against all shards.
        // If it returns false or throws, @Transactional rolls back the DB write.
        // Additionally, we restore the ledger entity state to prevent partial flushes.
        boolean quorumOk;
        try {
            quorumOk = quorumSyncService.proposeTransactionToQuorum(finalHash);
        } catch (Exception e) {
            // Quorum threw (e.g. SplitBrainException) — revert FULL entity state and
            // propagate
            log.error("[LedgerService] Quorum proposal threw exception: {}. Reverting entity.", e.getMessage());
            snapshot.restoreTo(ledger);
            throw new LedgerExceptions.LedgerSyncException("Quorum exception — transaction aborted: " + e.getMessage());
        }

        if (!quorumOk) {
            // Revert FULL entity state so @Transactional rollback is clean
            snapshot.restoreTo(ledger);
            throw new LedgerExceptions.LedgerSyncException(
                    "Failed to reach consensus quorum across shards for this balance operation.");
        }

        LedgerEntity saved = ledgerRepository.save(ledger);

        // Publish WebSocket event for real-time balance update
        balanceEventPublisher.publishBalanceUpdate(
                ledger.getWallet().getId(),
                ledger.getWallet().getName(),
                ledger.getWallet().getUser().getId(),
                ledger.getBalance(),
                amount,
                context);

        return saved;
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

    private String generateInitialHash(Long walletId) {
        String data = "GENESIS_" + walletId + "_" + System.currentTimeMillis();
        return hash.hash(data.toCharArray());
    }

    private String generateHash(LedgerEntity ledger) {
        String data = ledger.getWallet().getId() + "_" +
                ledger.getBalance().toPlainString() + "_" +
                ledger.getNonce() + "_" +
                ledger.getLastHash() + "_" +
                ledger.getContext() + "_" +
                System.currentTimeMillis();
        return hash.hash(data.toCharArray());
    }

    private String generateBalanceSignature(LedgerEntity ledger) {
        // Deterministic signature bound to the exact row, exact version (nonce), exact
        // user, and balance amount parameters
        String payload = "BALANCE_SIG:" +
                ledger.getWallet().getUser().getId() + ":" +
                ledger.getWallet().getId() + ":" +
                ledger.getNonce() + ":" +
                ledger.getBalance().toPlainString();
        return hash.hash(payload.toCharArray());
    }

    private void verifyBalanceIntegrity(LedgerEntity ledger) {
        if (ledger.getBalanceSignature() == null) {
            // Retrocompatibility: Just generated for older records dynamically
            ledger.setBalanceSignature(generateBalanceSignature(ledger));
            ledgerRepository.save(ledger);
            return;
        }

        String expectedSignature = generateBalanceSignature(ledger);
        if (!expectedSignature.equals(ledger.getBalanceSignature())) {
            // TAMPERING DETECTED
            try {
                UserDataBase user = ledger.getWallet().getUser();
                if (user != null) {
                    user.setIsActive(false);
                    userService.createUserInDataBase(user); // Force lock
                }
            } catch (Exception e) {
                // Ignore auxiliary errors, main goal is dropping exception below
            }
            throw new RuntimeException(
                    "CRITICAL: Banco de Dados corrompido ou adulteração direta detectada no Saldo. Conta bloqueada imediatamente para segurança.");
        }
    }

    public void validateWalletOwnership(WalletEntity wallet, Long userId) {
        if (wallet == null || wallet.getUser() == null || !wallet.getUser().getId().equals(userId)) {
            throw new RuntimeException("Carteira não encontrada ou não pertence ao usuário logado.");
        }
    }
}
