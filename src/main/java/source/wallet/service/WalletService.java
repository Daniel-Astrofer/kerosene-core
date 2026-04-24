package source.wallet.service;

import org.springframework.stereotype.Service;
import source.wallet.application.port.in.DeleteWalletUseCase;
import source.wallet.application.port.in.UpdateWalletUseCase;
import source.wallet.application.port.out.WalletCredentialsPort;
import source.wallet.application.service.WalletPersistenceSupport;
import source.wallet.application.service.WalletReader;
import source.wallet.dto.WalletRequestDTO;
import source.wallet.dto.WalletUpdateDTO;
import source.wallet.model.WalletEntity;

import java.util.List;

@Service
public class WalletService implements WalletContract {

    private final WalletReader walletReader;
    private final WalletPersistenceSupport walletPersistenceSupport;
    private final WalletCredentialsPort walletCredentialsPort;
    private final UpdateWalletUseCase updateWalletUseCase;
    private final DeleteWalletUseCase deleteWalletUseCase;

    public WalletService(
            WalletReader walletReader,
            WalletPersistenceSupport walletPersistenceSupport,
            WalletCredentialsPort walletCredentialsPort,
            UpdateWalletUseCase updateWalletUseCase,
            DeleteWalletUseCase deleteWalletUseCase) {
        this.walletReader = walletReader;
        this.walletPersistenceSupport = walletPersistenceSupport;
        this.walletCredentialsPort = walletCredentialsPort;
        this.updateWalletUseCase = updateWalletUseCase;
        this.deleteWalletUseCase = deleteWalletUseCase;
    }

    public void save(WalletEntity entity) {
        walletCredentialsPort.validateBip39Passphrase(entity.getPassphraseHash());
        walletPersistenceSupport.persistNew(entity);
    }

    public WalletEntity findByName(String name) {
        return walletReader.findByName(name);
    }

    public WalletEntity findByNameAndUserId(String name, Long userId) {
        return walletReader.findByNameAndUserId(name, userId);
    }

    public WalletEntity findById(Long id) {
        return walletReader.findById(id);
    }

    public List<WalletEntity> findAll() {
        return walletReader.findAll();
    }

    public WalletEntity findByPassphraseHash(String passphraseHash) {
        return walletReader.findByPassphraseHash(passphraseHash);
    }

    public WalletEntity findByDepositAddress(String depositAddress) {
        return walletReader.findByDepositAddress(depositAddress);
    }

    public WalletEntity findByLightningAddress(String lightningAddress) {
        return walletReader.findByLightningAddress(lightningAddress);
    }

    public boolean existsByUserIdAndName(Long id, String name) {
        return walletReader.existsByUserIdAndName(id, name);
    }

    public boolean existsByName(String name) {
        return walletReader.existsByName(name);
    }

    public List<WalletEntity> findByUserId(Long userId) {
        return walletReader.findByUserId(userId);
    }

    public WalletEntity findPrimaryWallet(Long userId) {
        return walletReader.findPrimaryWallet(userId);
    }

    public int incrementLastDerivedIndex(Long walletId) {
        return walletPersistenceSupport.incrementLastDerivedIndex(walletId);
    }

    public boolean deleteWallet(Long id, WalletRequestDTO wallet) {
        deleteWalletUseCase.deleteWallet(wallet, id);
        return true;
    }

    public void updateWallet(Long userId, WalletUpdateDTO dto) {
        updateWalletUseCase.updateWallet(dto, userId);
    }
}
