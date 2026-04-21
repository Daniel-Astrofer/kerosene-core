package source.wallet.application.port.in;

import source.wallet.dto.WalletUpdateDTO;

public interface UpdateWalletUseCase {

    void updateWallet(WalletUpdateDTO dto, Long userId);
}
