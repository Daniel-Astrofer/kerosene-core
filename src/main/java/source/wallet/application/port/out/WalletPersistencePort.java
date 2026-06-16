package source.wallet.application.port.out;

import source.wallet.model.WalletEntity;

import java.util.List;
import java.util.Optional;

public interface WalletPersistencePort {

    WalletEntity save(WalletEntity wallet);

    List<WalletEntity> saveAll(Iterable<WalletEntity> wallets);


    Optional<WalletEntity> findById(Long id);

    Optional<WalletEntity> findByIdForUpdate(Long id);

    List<WalletEntity> findAll();

    WalletEntity findByName(String name);

    WalletEntity findByPassphraseHash(String passphraseHash);

    List<WalletEntity> findByUserId(Long userId);

    boolean existsByName(String name);

    boolean existsByUserIdAndName(Long userId, String name);

    Optional<WalletEntity> findByUserIdAndName(Long userId, String name);

    Optional<WalletEntity> findByDepositAddress(String depositAddress);

    Optional<WalletEntity> findByDestinationHash(String destinationHash);

    List<WalletEntity> findTop500ByDestinationHashIsNullOrderByIdAsc();

    Optional<WalletEntity> findByLightningAddress(String lightningAddress);

    void delete(WalletEntity wallet);
}
