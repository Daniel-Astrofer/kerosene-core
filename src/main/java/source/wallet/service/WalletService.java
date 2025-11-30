package source.wallet.service;

import org.springframework.stereotype.Service;
import source.wallet.model.WalletEntity;
import source.wallet.repository.WalletRepository;

@Service
public class WalletService {

    private final WalletRepository walletRepository;

    public WalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    public void save(WalletEntity entity) {
        walletRepository.save(entity);
    }


}
