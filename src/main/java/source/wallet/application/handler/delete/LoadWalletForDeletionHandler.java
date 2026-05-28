package source.wallet.application.handler.delete;

import org.springframework.stereotype.Component;
import source.wallet.application.chain.AbstractWalletRequestHandler;
import source.wallet.application.context.DeleteWalletContext;
import source.wallet.application.service.WalletReader;
import source.wallet.domain.WalletNamingPolicy;
import source.wallet.exceptions.WalletExceptions;
import source.wallet.model.WalletEntity;

@Component
public class LoadWalletForDeletionHandler extends AbstractWalletRequestHandler<DeleteWalletContext> {

    private final WalletReader walletReader;

    public LoadWalletForDeletionHandler(WalletReader walletReader) {
        this.walletReader = walletReader;
    }

    @Override
    protected void doHandle(DeleteWalletContext context) {
        context.setNormalizedName(WalletNamingPolicy.normalizeName(context.getRequest().name()));

        WalletEntity wallet = walletReader.findByNameAndUserId(context.getRequest().name(), context.getUserId());
        if (wallet == null) {
            throw new WalletExceptions.WalletNoExists("wallet no exists");
        }

        context.setWallet(wallet);
    }
}
