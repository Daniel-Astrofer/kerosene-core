package source.wallet.application.port.in;

import source.wallet.dto.WalletRequestDTO;
import source.wallet.dto.WalletResponseDTO;

public interface CreateWalletUseCase {

    WalletResponseDTO createWallet(WalletRequestDTO dto, Long userId);
}
