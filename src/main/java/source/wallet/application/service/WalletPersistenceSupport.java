package source.wallet.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.wallet.application.port.out.WalletCredentialsPort;
import source.wallet.application.port.out.WalletPersistencePort;
import source.wallet.exceptions.WalletExceptions;
import source.wallet.model.WalletEntity;

@Service
@Transactional
public class WalletPersistenceSupport {

    private final WalletPersistencePort walletPersistencePort;
    private final WalletCredentialsPort walletCredentialsPort;

    public WalletPersistenceSupport(
            WalletPersistencePort walletPersistencePort,
            WalletCredentialsPort walletCredentialsPort) {
        this.walletPersistencePort = walletPersistencePort;
        this.walletCredentialsPort = walletCredentialsPort;
    }

    public WalletEntity persistNew(WalletEntity wallet) {
        wallet.setPassphraseHash(walletCredentialsPort.hashPassphrase(wallet.getPassphraseHash()));
        return walletPersistencePort.save(wallet);
    }

    public WalletEntity persist(WalletEntity wallet) {
        return walletPersistencePort.save(wallet);
    }

    public boolean matchesPassphrase(String rawPassphrase, WalletEntity wallet) {
        return rawPassphrase != null
                && wallet != null
                && walletCredentialsPort.matches(rawPassphrase, wallet.getPassphraseHash());
    }

    public void delete(WalletEntity wallet) {
        walletPersistencePort.delete(wallet);
    }

    public int incrementLastDerivedIndex(Long walletId) {
        WalletEntity wallet = walletPersistencePort.findByIdForUpdate(walletId)
                .orElseThrow(() -> new WalletExceptions.WalletNoExists("wallet not found"));

        Integer currentIndex = wallet.getLastDerivedIndex();
        int nextIndex = currentIndex == null ? 0 : currentIndex + 1;
        wallet.setLastDerivedIndex(nextIndex);
        walletPersistencePort.save(wallet);
        return nextIndex;
    }
}
