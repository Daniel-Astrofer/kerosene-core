package source.wallet.application.port.out;

import source.wallet.service.WalletCardProfile;

public interface WalletCardProfilePort {

    WalletCardProfile resolveProfile(Long userId);
}
