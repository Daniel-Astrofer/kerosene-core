package source.wallet.application.handler.create;

import org.springframework.stereotype.Component;
import source.wallet.application.chain.AbstractWalletRequestHandler;
import source.wallet.application.context.CreateWalletContext;
import source.wallet.application.port.out.WalletAddressProvisionPort;

@Component
public class AllocateWalletAddressHandler extends AbstractWalletRequestHandler<CreateWalletContext> {

    private final WalletAddressProvisionPort walletAddressProvisionPort;

    public AllocateWalletAddressHandler(WalletAddressProvisionPort walletAddressProvisionPort) {
        this.walletAddressProvisionPort = walletAddressProvisionPort;
    }

    @Override
    protected void doHandle(CreateWalletContext context) {
        walletAddressProvisionPort.allocate(
                context.getUserId(),
                context.getWallet(),
                "wallet-create:" + context.getWallet().getName(),
                false);
    }
}
