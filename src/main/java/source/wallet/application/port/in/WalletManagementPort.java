package source.wallet.application.port.in;

import source.wallet.dto.WalletRequestDTO;
import source.wallet.dto.WalletUpdateDTO;
import source.wallet.model.WalletEntity;

public interface WalletManagementPort {

    void save(WalletEntity entity);

    boolean deleteWallet(Long id, WalletRequestDTO wallet);

    void updateWallet(Long userId, WalletUpdateDTO dto);
}
