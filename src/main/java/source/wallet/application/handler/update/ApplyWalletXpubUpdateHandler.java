package source.wallet.application.handler.update;

import org.springframework.stereotype.Component;
import source.wallet.application.chain.AbstractWalletRequestHandler;
import source.wallet.application.context.UpdateWalletContext;
import source.wallet.application.port.out.WalletAddressDerivationPort;
import source.wallet.model.WalletEntity;

@Component
public class ApplyWalletXpubUpdateHandler extends AbstractWalletRequestHandler<UpdateWalletContext> {

    private final WalletAddressDerivationPort walletAddressDerivationPort;

    public ApplyWalletXpubUpdateHandler(WalletAddressDerivationPort walletAddressDerivationPort) {
        this.walletAddressDerivationPort = walletAddressDerivationPort;
    }

    @Override
    protected void doHandle(UpdateWalletContext context) {
        if (!context.isXpubChangeRequested()) {
            return;
        }

        WalletEntity wallet = context.getWallet();
        if (context.getNormalizedXpub() == null) {
            wallet.setXpub(null);
            wallet.setDepositAddress(null);
            wallet.setLastDerivedIndex(-1);
            return;
        }

        wallet.setXpub(context.getNormalizedXpub());
        wallet.setLastDerivedIndex(0);
        wallet.setDepositAddress(walletAddressDerivationPort.deriveAddressFromXpub(context.getNormalizedXpub(), 0));
    }
}
