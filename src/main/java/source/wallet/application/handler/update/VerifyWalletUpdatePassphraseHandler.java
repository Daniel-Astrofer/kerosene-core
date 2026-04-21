package source.wallet.application.handler.update;

import org.springframework.stereotype.Component;
import source.wallet.application.chain.AbstractWalletRequestHandler;
import source.wallet.application.context.UpdateWalletContext;
import source.wallet.application.service.WalletPersistenceSupport;
import source.wallet.exceptions.WalletExceptions;

@Component
public class VerifyWalletUpdatePassphraseHandler extends AbstractWalletRequestHandler<UpdateWalletContext> {

    private final WalletPersistenceSupport walletPersistenceSupport;

    public VerifyWalletUpdatePassphraseHandler(WalletPersistenceSupport walletPersistenceSupport) {
        this.walletPersistenceSupport = walletPersistenceSupport;
    }

    @Override
    protected void doHandle(UpdateWalletContext context) {
        if (!walletPersistenceSupport.matchesPassphrase(context.getRequest().passphrase(), context.getWallet())) {
            throw new WalletExceptions.WalletNoExists("invalid passphrase for update");
        }
    }
}
