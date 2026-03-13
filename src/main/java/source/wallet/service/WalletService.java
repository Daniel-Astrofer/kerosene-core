package source.wallet.service;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import source.auth.application.service.authentication.contracts.SignupVerifier;
import source.auth.application.service.cripto.contracts.Hasher;
import source.wallet.dto.WalletRequestDTO;
import source.wallet.dto.WalletUpdateDTO;
import source.wallet.exceptions.WalletExceptions;
import source.wallet.model.WalletEntity;
import source.wallet.repository.WalletRepository;
import java.util.List;

@Transactional
@Service
public class WalletService implements WalletContract {

    private final WalletRepository walletRepository;
    private final Hasher hash;
    private final SignupVerifier verify;

    public WalletService(WalletRepository walletRepository,
            @Qualifier("Argon2Hasher") Hasher hash, SignupVerifier verify) {
        this.walletRepository = walletRepository;
        this.hash = hash;
        this.verify = verify;
    }

    public void save(WalletEntity entity) {
        // Validate BIP39 on the raw passphrase BEFORE hashing — after hashing it is
        // write-only
        verify.checkPassphraseBip39(entity.getPassphraseHash().toCharArray());
        // Hash with Argon2id — hashing is always centralised here, never in the caller
        entity.setPassphraseHash(hash.hash(entity.getPassphraseHash().toCharArray()));
        walletRepository.save(entity);
    }

    public WalletEntity findByName(String name) {
        String upperName = name != null ? name.toUpperCase() : null;
        return walletRepository.findByName(upperName);
    }

    public WalletEntity findByNameAndUserId(String name, Long userId) {
        String upperName = name != null ? name.toUpperCase() : null;
        return walletRepository.findByUserIdAndName(userId, upperName).orElse(null);
    }

    public WalletEntity findById(Long id) {
        return walletRepository.findById(id).orElse(null);
    }

    public WalletEntity findByPassphraseHash(String passphraseHash) {
        return walletRepository.findByPassphraseHash(passphraseHash);
    }

    public boolean existsByUserIdAndName(Long id, String name) {
        return walletRepository.existsByUserIdAndName(id, name != null ? name.toUpperCase() : null);
    }

    public boolean existsByName(String name) {
        return walletRepository.existsByName(name != null ? name.toUpperCase() : null);
    }

    public List<WalletEntity> findByUserId(Long userId) {
        return walletRepository.findByUserId(userId);
    }

    public boolean deleteWallet(Long id, WalletRequestDTO wallet) {
        String walletNameUpperCase = wallet.name() != null ? wallet.name().toUpperCase() : null;
        WalletEntity dbWallet = walletRepository.findByUserIdAndName(id, walletNameUpperCase)
                .orElseThrow(() -> new WalletExceptions.WalletNoExists("wallet no exists"));

        // Use Argon2 verify (BCrypt-style PHC comparison) — never hash-then-compare
        if (!hash.verify(wallet.passphrase().toCharArray(), dbWallet.getPassphraseHash())) {
            throw new WalletExceptions.WalletNoExists("invalid passphrase for deletion");
        }

        walletRepository.delete(dbWallet);
        return true;
    }

    public void updateWallet(Long userId, WalletUpdateDTO dto) {
        String dtoNameUpperCase = dto.name() != null ? dto.name().toUpperCase() : null;
        WalletEntity wallet = walletRepository.findByUserIdAndName(userId, dtoNameUpperCase)
                .orElseThrow(() -> new WalletExceptions.WalletNoExists("wallet not found"));

        String newNameUpper = dto.newName() != null ? dto.newName().toUpperCase() : null;

        if (newNameUpper != null && !newNameUpper.equals(dtoNameUpperCase)) {
            if (walletRepository.existsByUserIdAndName(userId, newNameUpper)) {
                throw new WalletExceptions.WalletNameAlredyExists("new name already in use");
            }
        }

        if (newNameUpper != null && !newNameUpper.isEmpty()) {
            wallet.setName(dto.newName());
        }
        walletRepository.save(wallet);
    }
}
