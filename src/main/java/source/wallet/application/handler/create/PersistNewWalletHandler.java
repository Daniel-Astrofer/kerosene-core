package source.wallet.application.handler.create;

import org.springframework.stereotype.Component;
import source.wallet.application.chain.AbstractWalletRequestHandler;
import source.wallet.application.context.CreateWalletContext;
import source.wallet.application.service.WalletPersistenceSupport;

@Component
public class PersistNewWalletHandler extends AbstractWalletRequestHandler<CreateWalletContext> {

    private final WalletPersistenceSupport walletPersistenceSupport;

    public PersistNewWalletHandler(WalletPersistenceSupport walletPersistenceSupport) {
        this.walletPersistenceSupport = walletPersistenceSupport;
    }

    @Override
    protected void doHandle(CreateWalletContext context) {
        context.setWallet(walletPersistenceSupport.persistNew(context.getWallet()));
    }
}
