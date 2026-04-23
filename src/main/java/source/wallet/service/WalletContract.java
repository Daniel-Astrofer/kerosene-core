package source.wallet.service;

import source.wallet.dto.WalletRequestDTO;
import source.wallet.dto.WalletUpdateDTO;
import source.wallet.model.WalletEntity;

import java.util.List;

public interface WalletContract {
    void save(WalletEntity entity);

    WalletEntity findByName(String name);

    WalletEntity findByNameAndUserId(String name, Long userId);

    WalletEntity findById(Long id);

    List<WalletEntity> findAll();

    WalletEntity findByPassphraseHash(String passphraseHash);

    WalletEntity findByDepositAddress(String depositAddress);

    boolean existsByUserIdAndName(Long id, String name);

    List<WalletEntity> findByUserId(Long userId);

    boolean deleteWallet(Long id, WalletRequestDTO wallet);

    void updateWallet(Long userId, WalletUpdateDTO dto);

    int incrementLastDerivedIndex(Long walletId);
}
