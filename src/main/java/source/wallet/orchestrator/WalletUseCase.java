package source.wallet.orchestrator;

import org.springframework.stereotype.Service;
import source.wallet.application.port.in.CreateWalletUseCase;
import source.wallet.application.port.in.DeleteWalletUseCase;
import source.wallet.application.port.in.QueryWalletUseCase;
import source.wallet.application.port.in.UpdateWalletUseCase;
import source.wallet.dto.WalletRequestDTO;
import source.wallet.dto.WalletResponseDTO;
import source.wallet.dto.WalletUpdateDTO;

import java.util.List;

@Service
public class WalletUseCase {

    private final CreateWalletUseCase createWalletUseCase;
    private final DeleteWalletUseCase deleteWalletUseCase;
    private final QueryWalletUseCase queryWalletUseCase;
    private final UpdateWalletUseCase updateWalletUseCase;

    public WalletUseCase(
            CreateWalletUseCase createWalletUseCase,
            DeleteWalletUseCase deleteWalletUseCase,
            QueryWalletUseCase queryWalletUseCase,
            UpdateWalletUseCase updateWalletUseCase) {
        this.createWalletUseCase = createWalletUseCase;
        this.deleteWalletUseCase = deleteWalletUseCase;
        this.queryWalletUseCase = queryWalletUseCase;
        this.updateWalletUseCase = updateWalletUseCase;
    }

    public WalletResponseDTO createWallet(WalletRequestDTO dto, Long userId) {
        return createWalletUseCase.createWallet(dto, userId);
    }

    public void deleteWallet(WalletRequestDTO dto, Long userId) {
        deleteWalletUseCase.deleteWallet(dto, userId);
    }

    public List<WalletResponseDTO> getAllWallets(Long userId) {
        return queryWalletUseCase.getAllWallets(userId);
    }

    public WalletResponseDTO getWalletByName(String name, Long userId) {
        return queryWalletUseCase.getWalletByName(name, userId);
    }

    public void updateWallet(WalletUpdateDTO dto, Long userId) {
        updateWalletUseCase.updateWallet(dto, userId);
    }
}
