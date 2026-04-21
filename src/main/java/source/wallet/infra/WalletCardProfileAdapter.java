package source.wallet.infra;

import org.springframework.stereotype.Component;
import source.wallet.application.port.out.WalletCardProfilePort;
import source.wallet.service.WalletCardProfile;
import source.wallet.service.WalletCardProfileService;

@Component
public class WalletCardProfileAdapter implements WalletCardProfilePort {

    private final WalletCardProfileService walletCardProfileService;

    public WalletCardProfileAdapter(WalletCardProfileService walletCardProfileService) {
        this.walletCardProfileService = walletCardProfileService;
    }

    @Override
    public WalletCardProfile resolveProfile(Long userId) {
        return walletCardProfileService.resolveProfile(userId);
    }
}
