package source.wallet.application.handler.update;

import org.springframework.stereotype.Component;
import source.wallet.application.chain.AbstractWalletRequestHandler;
import source.wallet.application.context.UpdateWalletContext;
import source.wallet.domain.WalletNamingPolicy;

@Component
public class ValidateUpdateWalletRequestHandler extends AbstractWalletRequestHandler<UpdateWalletContext> {

    @Override
    protected void doHandle(UpdateWalletContext context) {
        context.setNormalizedNewName(WalletNamingPolicy.normalizeName(context.getRequest().newName()));
        context.setNormalizedXpub(WalletNamingPolicy.normalizeOptionalXpub(context.getRequest().newXpub()));

        if (context.getNormalizedNewName() == null && !context.isXpubChangeRequested()) {
            throw new IllegalArgumentException("At least one wallet field must be updated.");
        }
    }
}
