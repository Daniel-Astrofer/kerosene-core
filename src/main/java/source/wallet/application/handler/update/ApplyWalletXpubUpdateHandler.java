package source.wallet.application.handler.update;

import org.springframework.stereotype.Component;
import source.wallet.application.chain.AbstractWalletRequestHandler;
import source.wallet.application.context.UpdateWalletContext;
import source.wallet.model.WalletEntity;
import source.wallet.model.WalletMode;

@Component
public class ApplyWalletXpubUpdateHandler extends AbstractWalletRequestHandler<UpdateWalletContext> {

    @Override
    protected void doHandle(UpdateWalletContext context) {
        if (!context.isXpubChangeRequested() && !context.isWalletModeChangeRequested()) {
            return;
        }

        WalletEntity wallet = context.getWallet();
        WalletMode targetMode = context.isWalletModeChangeRequested()
                ? context.getNormalizedWalletMode()
                : wallet.getWalletMode();

        if (targetMode == WalletMode.SELF_CUSTODY) {
            String xpub = context.isXpubChangeRequested() ? context.getNormalizedXpub() : wallet.getXpub();
            if (xpub == null || xpub.isBlank()) {
                throw new IllegalArgumentException("Self-custody wallets require a valid XPUB.");
            }

            wallet.setWalletMode(WalletMode.SELF_CUSTODY);
            wallet.setXpub(xpub);
            wallet.setLastDerivedIndex(-1);
            wallet.setDepositAddress(null);
            wallet.setExternalWalletReference(null);
            return;
        }

        wallet.setWalletMode(WalletMode.KEROSENE);
        wallet.setXpub(null);
        wallet.setDepositAddress(null);
        wallet.setExternalWalletReference(null);
        wallet.setLastDerivedIndex(-1);
    }
}
