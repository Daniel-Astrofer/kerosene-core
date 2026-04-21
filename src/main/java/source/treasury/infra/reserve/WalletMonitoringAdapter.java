package source.treasury.infra.reserve;

import org.springframework.stereotype.Component;
import source.treasury.application.port.out.WalletMonitoringPort;
import source.treasury.domain.model.MonitoredWallet;
import source.wallet.repository.WalletRepository;

import java.util.List;

@Component
public class WalletMonitoringAdapter implements WalletMonitoringPort {

    private final WalletRepository walletRepository;

    public WalletMonitoringAdapter(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Override
    public List<MonitoredWallet> findAll() {
        return walletRepository.findAll().stream()
                .map(wallet -> new MonitoredWallet(
                        wallet.getId(),
                        wallet.getXpub(),
                        wallet.getLastDerivedIndex(),
                        wallet.getDepositAddress()))
                .toList();
    }
}
