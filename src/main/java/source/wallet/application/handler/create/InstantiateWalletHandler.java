package source.wallet.application.handler.create;

import org.springframework.stereotype.Component;
import source.wallet.application.chain.AbstractWalletRequestHandler;
import source.wallet.application.context.CreateWalletContext;
import source.wallet.application.port.out.WalletCredentialsPort;
import source.wallet.model.WalletEntity;
import source.wallet.model.WalletMode;

@Component
public class InstantiateWalletHandler extends AbstractWalletRequestHandler<CreateWalletContext> {

    private final WalletCredentialsPort walletCredentialsPort;

    public InstantiateWalletHandler(WalletCredentialsPort walletCredentialsPort) {
        this.walletCredentialsPort = walletCredentialsPort;
    }

    @Override
    protected void doHandle(CreateWalletContext context) {
        WalletEntity wallet = new WalletEntity();
        wallet.setPassphraseHash(context.getRequest().passphrase());
        wallet.setName(context.getNormalizedName());
        wallet.setUser(context.getUser());
        wallet.setXpub(context.getNormalizedXpub());
        wallet.setWalletMode(context.getNormalizedWalletMode() != null
                ? context.getNormalizedWalletMode()
                : WalletMode.KEROSENE);

        String totpSecret = walletCredentialsPort.generateTotpSecret();
        wallet.setTotpSecret(totpSecret);

        context.setTotpSecret(totpSecret);
        context.setWallet(wallet);
    }
}
