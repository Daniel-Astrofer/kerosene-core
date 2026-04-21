package source.wallet.application.handler.create;

import org.springframework.stereotype.Component;
import source.wallet.application.chain.AbstractWalletRequestHandler;
import source.wallet.application.context.CreateWalletContext;
import source.wallet.application.port.out.WalletLedgerPort;

@Component
public class CreateWalletLedgerHandler extends AbstractWalletRequestHandler<CreateWalletContext> {

    private final WalletLedgerPort walletLedgerPort;

    public CreateWalletLedgerHandler(WalletLedgerPort walletLedgerPort) {
        this.walletLedgerPort = walletLedgerPort;
    }

    @Override
    protected void doHandle(CreateWalletContext context) {
        walletLedgerPort.createLedger(context.getWallet(), "Initial ledger for new wallet");
    }
}
