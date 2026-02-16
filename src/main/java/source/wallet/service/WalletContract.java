package source.wallet.service;

import source.wallet.dto.WalletDTO;
import source.wallet.model.WalletEntity;

import java.util.List;

public interface WalletContract {
    void save(WalletEntity entity);

    WalletEntity findByName(String name);

    WalletEntity findByAddress(String address);

    boolean existsByName(String name);

    List<WalletEntity> findByUserId(Long userId);

    boolean deleteWallet(Long id, WalletDTO wallet);

    void updateWallet(Long userId, WalletDTO dto);
}
