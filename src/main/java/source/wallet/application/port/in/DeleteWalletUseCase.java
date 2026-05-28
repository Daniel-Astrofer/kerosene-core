package source.wallet.application.port.in;

import source.wallet.dto.WalletRequestDTO;

public interface DeleteWalletUseCase {

    void deleteWallet(WalletRequestDTO dto, Long userId);
}
