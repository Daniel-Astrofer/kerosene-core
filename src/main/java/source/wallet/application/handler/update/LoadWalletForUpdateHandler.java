package source.wallet.application.handler.update;

import org.springframework.stereotype.Component;
import source.wallet.application.chain.AbstractWalletRequestHandler;
import source.wallet.application.context.UpdateWalletContext;
import source.wallet.application.service.WalletReader;
import source.wallet.domain.WalletNamingPolicy;
import source.wallet.exceptions.WalletExceptions;
import source.wallet.model.WalletEntity;

@Component
public class LoadWalletForUpdateHandler extends AbstractWalletRequestHandler<UpdateWalletContext> {

    private final WalletReader walletReader;

    public LoadWalletForUpdateHandler(WalletReader walletReader) {
        this.walletReader = walletReader;
    }

    @Override
    protected void doHandle(UpdateWalletContext context) {
        context.setNormalizedCurrentName(WalletNamingPolicy.normalizeName(context.getRequest().name()));

        WalletEntity wallet = walletReader.findByNameAndUserId(context.getRequest().name(), context.getUserId());
        if (wallet == null) {
            throw new WalletExceptions.WalletNoExists("wallet not found");
        }

        context.setWallet(wallet);
    }
}
