package source.wallet.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.wallet.application.port.out.WalletPersistencePort;
import source.wallet.domain.WalletNamingPolicy;
import source.wallet.model.WalletEntity;

import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class WalletReader {

    private final WalletPersistencePort walletPersistencePort;

    public WalletReader(WalletPersistencePort walletPersistencePort) {
        this.walletPersistencePort = walletPersistencePort;
    }

    public WalletEntity findByName(String name) {
        return walletPersistencePort.findByName(WalletNamingPolicy.normalizeName(name));
    }

    public WalletEntity findByNameAndUserId(String name, Long userId) {
        return walletPersistencePort.findByUserIdAndName(userId, WalletNamingPolicy.normalizeName(name)).orElse(null);
    }

    public WalletEntity findById(Long id) {
        return walletPersistencePort.findById(id).orElse(null);
    }

    public List<WalletEntity> findAll() {
        return walletPersistencePort.findAll();
    }

    public WalletEntity findByPassphraseHash(String passphraseHash) {
        return walletPersistencePort.findByPassphraseHash(passphraseHash);
    }

    public WalletEntity findByDepositAddress(String depositAddress) {
        return walletPersistencePort.findByDepositAddress(depositAddress).orElse(null);
    }

    public WalletEntity findByLightningAddress(String lightningAddress) {
        return walletPersistencePort.findByLightningAddress(lightningAddress).orElse(null);
    }

    public boolean existsByUserIdAndName(Long userId, String name) {
        return walletPersistencePort.existsByUserIdAndName(userId, WalletNamingPolicy.normalizeName(name));
    }

    public boolean existsByName(String name) {
        return walletPersistencePort.existsByName(WalletNamingPolicy.normalizeName(name));
    }

    public List<WalletEntity> findByUserId(Long userId) {
        return walletPersistencePort.findByUserId(userId);
    }

    public WalletEntity findPrimaryWallet(Long userId) {
        return findByUserId(userId).stream()
                .filter(wallet -> wallet.getId() != null)
                .min(Comparator.comparing(WalletEntity::getId))
                .orElse(null);
    }
}
