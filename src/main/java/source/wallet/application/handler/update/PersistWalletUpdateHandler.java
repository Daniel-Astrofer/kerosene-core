package source.wallet.application.handler.update;

import org.springframework.stereotype.Component;
import source.wallet.application.chain.AbstractWalletRequestHandler;
import source.wallet.application.context.UpdateWalletContext;
import source.wallet.application.service.WalletPersistenceSupport;

@Component
public class PersistWalletUpdateHandler extends AbstractWalletRequestHandler<UpdateWalletContext> {

    private final WalletPersistenceSupport walletPersistenceSupport;

    public PersistWalletUpdateHandler(WalletPersistenceSupport walletPersistenceSupport) {
        this.walletPersistenceSupport = walletPersistenceSupport;
    }

    @Override
    protected void doHandle(UpdateWalletContext context) {
        walletPersistenceSupport.persist(context.getWallet());
    }
}
