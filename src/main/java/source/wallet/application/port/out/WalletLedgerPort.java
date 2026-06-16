package source.wallet.application.port.out;

import source.wallet.model.WalletEntity;

public interface WalletLedgerPort {

    void createLedger(WalletEntity wallet, String context);
}
