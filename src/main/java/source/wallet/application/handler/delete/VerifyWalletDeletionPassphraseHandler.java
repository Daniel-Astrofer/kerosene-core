package source.wallet.application.handler.delete;

import org.springframework.stereotype.Component;
import source.wallet.application.chain.AbstractWalletRequestHandler;
import source.wallet.application.context.DeleteWalletContext;
import source.wallet.application.service.WalletPersistenceSupport;
import source.wallet.exceptions.WalletExceptions;

@Component
public class VerifyWalletDeletionPassphraseHandler extends AbstractWalletRequestHandler<DeleteWalletContext> {

    private final WalletPersistenceSupport walletPersistenceSupport;

    public VerifyWalletDeletionPassphraseHandler(WalletPersistenceSupport walletPersistenceSupport) {
        this.walletPersistenceSupport = walletPersistenceSupport;
    }

    @Override
    protected void doHandle(DeleteWalletContext context) {
        if (!walletPersistenceSupport.matchesPassphrase(context.getRequest().passphrase(), context.getWallet())) {
            throw new WalletExceptions.WalletNoExists("invalid passphrase for deletion");
        }
    }
}
