package source.wallet.application.port.in;

import source.wallet.exceptions.WalletExceptions;
import source.wallet.model.WalletEntity;

import java.util.List;

public interface WalletLookupPort {

    WalletEntity findByName(String name);

    WalletEntity findByNameAndUserId(String name, Long userId);

    WalletEntity findById(Long id);

    List<WalletEntity> findAll();

    WalletEntity findByPassphraseHash(String passphraseHash);

    WalletEntity findByDepositAddress(String depositAddress);

    WalletEntity findByDestinationHash(String destinationHash);

    WalletEntity findByLightningAddress(String lightningAddress);

    boolean existsByUserIdAndName(Long userId, String name);

    boolean existsByName(String name);

    List<WalletEntity> findByUserId(Long userId);

    WalletEntity findPrimaryWallet(Long userId);

    default WalletEntity requireByNameAndUserId(String name, Long userId) {
        WalletEntity wallet = findByNameAndUserId(name, userId);
        if (wallet == null) {
            throw new WalletExceptions.WalletNoExists("wallet not found");
        }
        return wallet;
    }

    default WalletEntity requirePrimaryWallet(Long userId) {
        WalletEntity wallet = findPrimaryWallet(userId);
        if (wallet == null) {
            throw new WalletExceptions.WalletNoExists("wallet not found");
        }
        return wallet;
    }
}
