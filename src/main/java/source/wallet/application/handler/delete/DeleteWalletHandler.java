package source.wallet.application.handler.delete;

import org.springframework.stereotype.Component;
import source.wallet.application.chain.AbstractWalletRequestHandler;
import source.wallet.application.context.DeleteWalletContext;
import source.wallet.application.service.WalletPersistenceSupport;

@Component
public class DeleteWalletHandler extends AbstractWalletRequestHandler<DeleteWalletContext> {

    private final WalletPersistenceSupport walletPersistenceSupport;

    public DeleteWalletHandler(WalletPersistenceSupport walletPersistenceSupport) {
        this.walletPersistenceSupport = walletPersistenceSupport;
    }

    @Override
    protected void doHandle(DeleteWalletContext context) {
        walletPersistenceSupport.delete(context.getWallet());
    }
}
