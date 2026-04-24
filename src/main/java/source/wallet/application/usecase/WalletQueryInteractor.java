package source.wallet.application.usecase;

import org.springframework.stereotype.Service;
import source.wallet.application.port.in.QueryWalletUseCase;
import source.wallet.application.port.out.WalletCardProfilePort;
import source.wallet.application.service.WalletReader;
import source.wallet.application.service.WalletResponseAssembler;
import source.wallet.dto.WalletResponseDTO;
import source.wallet.exceptions.WalletExceptions;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletCardLifecycleService;
import source.wallet.service.WalletCardProfile;

import java.util.List;

@Service
public class WalletQueryInteractor implements QueryWalletUseCase {

    private final WalletReader walletReader;
    private final WalletCardProfilePort walletCardProfilePort;
    private final WalletResponseAssembler walletResponseAssembler;
    private final WalletCardLifecycleService walletCardLifecycleService;

    public WalletQueryInteractor(
            WalletReader walletReader,
            WalletCardProfilePort walletCardProfilePort,
            WalletResponseAssembler walletResponseAssembler,
            WalletCardLifecycleService walletCardLifecycleService) {
        this.walletReader = walletReader;
        this.walletCardProfilePort = walletCardProfilePort;
        this.walletResponseAssembler = walletResponseAssembler;
        this.walletCardLifecycleService = walletCardLifecycleService;
    }

    @Override
    public List<WalletResponseDTO> getAllWallets(Long userId) {
        WalletCardProfile cardProfile = walletCardProfilePort.resolveProfile(userId);
        return walletReader.findByUserId(userId).stream()
                .map(wallet -> walletResponseAssembler.toResponse(
                        wallet,
                        cardProfile,
                        walletCardLifecycleService.resolve(wallet)))
                .toList();
    }

    @Override
    public WalletResponseDTO getWalletByName(String name, Long userId) {
        WalletEntity wallet = walletReader.findByNameAndUserId(name, userId);
        if (wallet == null) {
            throw new WalletExceptions.WalletNoExists("wallet not found or does not belong to you");
        }
        return walletResponseAssembler.toResponse(
                wallet,
                walletCardProfilePort.resolveProfile(userId),
                walletCardLifecycleService.resolve(wallet));
    }
}
