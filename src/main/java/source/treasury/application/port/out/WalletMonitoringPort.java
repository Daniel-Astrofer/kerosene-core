package source.treasury.application.port.out;

import source.treasury.domain.model.MonitoredWallet;

import java.util.List;

public interface WalletMonitoringPort {

    List<MonitoredWallet> findAll();
}
