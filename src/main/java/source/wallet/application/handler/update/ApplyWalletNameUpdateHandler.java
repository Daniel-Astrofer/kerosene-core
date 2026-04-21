package source.wallet.application.handler.update;

import org.springframework.stereotype.Component;
import source.wallet.application.chain.AbstractWalletRequestHandler;
import source.wallet.application.context.UpdateWalletContext;

@Component
public class ApplyWalletNameUpdateHandler extends AbstractWalletRequestHandler<UpdateWalletContext> {

    @Override
    protected void doHandle(UpdateWalletContext context) {
        if (context.getNormalizedNewName() != null) {
            context.getWallet().setName(context.getNormalizedNewName());
        }
    }
}
