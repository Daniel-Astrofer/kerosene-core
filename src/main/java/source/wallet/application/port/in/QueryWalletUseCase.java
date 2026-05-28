package source.wallet.application.port.in;

import source.wallet.dto.WalletResponseDTO;

import java.util.List;

public interface QueryWalletUseCase {

    List<WalletResponseDTO> getAllWallets(Long userId);

    WalletResponseDTO getWalletByName(String name, Long userId);
}
