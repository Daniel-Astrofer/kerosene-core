package source.wallet.application.handler.update;

import org.springframework.stereotype.Component;
import source.wallet.application.chain.AbstractWalletRequestHandler;
import source.wallet.application.context.UpdateWalletContext;
import source.wallet.application.service.WalletReader;
import source.wallet.exceptions.WalletExceptions;

@Component
public class EnsureWalletNameAvailabilityHandler extends AbstractWalletRequestHandler<UpdateWalletContext> {

    private final WalletReader walletReader;

    public EnsureWalletNameAvailabilityHandler(WalletReader walletReader) {
        this.walletReader = walletReader;
    }

    @Override
    protected void doHandle(UpdateWalletContext context) {
        String newName = context.getNormalizedNewName();
        if (newName != null
                && !newName.equals(context.getNormalizedCurrentName())
                && walletReader.existsByUserIdAndName(context.getUserId(), newName)) {
            throw new WalletExceptions.WalletNameAlreadyExists("new name already in use");
        }
    }
}
