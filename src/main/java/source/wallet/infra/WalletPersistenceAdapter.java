package source.wallet.infra;

import org.springframework.stereotype.Component;
import source.wallet.application.port.out.WalletPersistencePort;
import source.wallet.model.WalletEntity;
import source.wallet.repository.WalletRepository;

import java.util.List;
import java.util.Optional;

@Component
public class WalletPersistenceAdapter implements WalletPersistencePort {

    private final WalletRepository walletRepository;

    public WalletPersistenceAdapter(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Override
    public WalletEntity save(WalletEntity wallet) {
        return walletRepository.save(wallet);
    }

    @Override
    public List<WalletEntity> saveAll(Iterable<WalletEntity> wallets) {
        return walletRepository.saveAll(wallets);
    }

    @Override
    public Optional<WalletEntity> findById(Long id) {
        return walletRepository.findById(id);
    }

    @Override
    public Optional<WalletEntity> findByIdForUpdate(Long id) {
        return walletRepository.findByIdForUpdate(id);
    }

    @Override
    public List<WalletEntity> findAll() {
        return walletRepository.findAll();
    }

    @Override
    public WalletEntity findByName(String name) {
        return walletRepository.findByName(name);
    }

    @Override
    public WalletEntity findByPassphraseHash(String passphraseHash) {
        return walletRepository.findByPassphraseHash(passphraseHash);
    }

    @Override
    public List<WalletEntity> findByUserId(Long userId) {
        return walletRepository.findByUserId(userId);
    }

    @Override
    public boolean existsByName(String name) {
        return walletRepository.existsByName(name);
    }

    @Override
    public boolean existsByUserIdAndName(Long userId, String name) {
        return walletRepository.existsByUserIdAndName(userId, name);
    }

    @Override
    public Optional<WalletEntity> findByUserIdAndName(Long userId, String name) {
        return walletRepository.findByUserIdAndName(userId, name);
    }

    @Override
    public Optional<WalletEntity> findByDepositAddress(String depositAddress) {
        return walletRepository.findByDepositAddress(depositAddress);
    }

    @Override
    public Optional<WalletEntity> findByDestinationHash(String destinationHash) {
        return walletRepository.findByDestinationHash(destinationHash);
    }

    @Override
    public List<WalletEntity> findTop500ByDestinationHashIsNullOrderByIdAsc() {
        return walletRepository.findTop500ByDestinationHashIsNullOrderByIdAsc();
    }

    @Override
    public Optional<WalletEntity> findByLightningAddress(String lightningAddress) {
        return walletRepository.findByLightningAddress(lightningAddress);
    }

    @Override
    public void delete(WalletEntity wallet) {
        walletRepository.delete(wallet);
    }
}
