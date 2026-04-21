package source.wallet.infra;

import org.springframework.stereotype.Component;
import source.ledger.service.LedgerService;
import source.wallet.application.port.out.WalletLedgerPort;
import source.wallet.model.WalletEntity;

@Component
public class WalletLedgerAdapter implements WalletLedgerPort {

    private final LedgerService ledgerService;

    public WalletLedgerAdapter(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @Override
    public void createLedger(WalletEntity wallet, String context) {
        ledgerService.createLedger(wallet, context);
    }
}
