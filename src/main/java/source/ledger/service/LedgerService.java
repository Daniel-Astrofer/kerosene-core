package source.ledger.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.auth.application.service.cripto.contracts.Hasher;
import source.ledger.dto.LedgerDTO;
import source.ledger.entity.LedgerEntity;
import source.ledger.exceptions.LedgerExceptions;
import source.ledger.repository.LedgerRepository;
import source.wallet.model.WalletEntity;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class LedgerService implements LedgerContract{

    private final LedgerRepository ledgerRepository;
    private final Hasher hash;

    public LedgerService(LedgerRepository ledgerRepository,
                         @Qualifier("SHAHasher") Hasher hash) {
        this.ledgerRepository = ledgerRepository;
        this.hash = hash;
    }


    @Override
    @Transactional
    public LedgerEntity createLedger(WalletEntity wallet, String context) {
        if (ledgerRepository.existsByWalletId(wallet.getId())) {
            throw new LedgerExceptions.LedgerAlreadyExistsException("Ledger already exists for this wallet");
        }

        LedgerEntity ledger = new LedgerEntity(wallet, context);
        ledger.setLastHash(generateInitialHash(wallet.getId()));
        
        return ledgerRepository.save(ledger);
    }

    @Override
    public LedgerEntity findByWalletId(Long walletId) {
        return ledgerRepository.findByWalletId(walletId)
                .orElseThrow(() -> new LedgerExceptions.LedgerNotFoundException("Ledger not found for wallet ID: " + walletId));
    }

    @Override
    public List<LedgerEntity> findByUserId(Long userId) {
        return ledgerRepository.findByWalletUserId(userId);
    }

    @Override
    @Transactional
    public LedgerEntity updateBalance(Long walletId, BigDecimal amount, String context) {
        LedgerEntity ledger = findByWalletId(walletId);
        
        // Validate sufficient balance for debit operations
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal newBalance = ledger.getBalance().add(amount);
            if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                throw new LedgerExceptions.InsufficientBalanceException("Insufficient balance for this operation");
            }
        }
        
        ledger.updateBalance(amount);
        ledger.incrementNonce();
        ledger.setContext(context);
        ledger.setLastHash(generateHash(ledger));
        
        return ledgerRepository.save(ledger);
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
    public List<LedgerDTO> toDTOList(List<LedgerEntity> ledgers) {
        return ledgers.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }


    private String generateInitialHash(Long walletId) {
        String data = "GENESIS_" + walletId + "_" + System.currentTimeMillis();
        return hash.hash(data);
    }

    private String generateHash(LedgerEntity ledger) {
        String data = ledger.getWallet().getId() + "_" +
                     ledger.getBalance().toString() + "_" +
                     ledger.getNonce() + "_" +
                     ledger.getLastHash() + "_" +
                     ledger.getContext() + "_" +
                     System.currentTimeMillis();
        return hash.hash(data);
    }
    public void validateWalletOwnership(WalletEntity wallet, Long userId) {
        if (wallet == null || !wallet.getUser().getId().equals(userId)) {
            throw new RuntimeException("Wallet not found or does not belong to you");
        }
    }

}
