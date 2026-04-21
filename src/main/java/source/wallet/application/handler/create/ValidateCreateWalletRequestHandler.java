package source.wallet.application.handler.create;

import org.springframework.stereotype.Component;
import source.wallet.application.chain.AbstractWalletRequestHandler;
import source.wallet.application.context.CreateWalletContext;
import source.wallet.application.port.out.WalletCredentialsPort;
import source.wallet.application.service.WalletReader;
import source.wallet.domain.WalletNamingPolicy;
import source.wallet.exceptions.WalletExceptions;

@Component
public class ValidateCreateWalletRequestHandler extends AbstractWalletRequestHandler<CreateWalletContext> {

    private final WalletReader walletReader;
    private final WalletCredentialsPort walletCredentialsPort;

    public ValidateCreateWalletRequestHandler(
            WalletReader walletReader,
            WalletCredentialsPort walletCredentialsPort) {
        this.walletReader = walletReader;
        this.walletCredentialsPort = walletCredentialsPort;
    }

    @Override
    protected void doHandle(CreateWalletContext context) {
        context.setNormalizedName(WalletNamingPolicy.normalizeName(context.getRequest().name()));
        context.setNormalizedXpub(WalletNamingPolicy.normalizeOptionalXpub(context.getRequest().xpub()));

        walletCredentialsPort.validateBip39Passphrase(context.getRequest().passphrase());

        if (walletReader.existsByUserIdAndName(context.getUserId(), context.getNormalizedName())) {
            throw new WalletExceptions.WalletNameAlreadyExists("you are using this name");
        }
    }
}
