package source.wallet.application.handler.create;

import org.springframework.stereotype.Component;
import source.wallet.application.chain.AbstractWalletRequestHandler;
import source.wallet.application.context.CreateWalletContext;
import source.wallet.application.port.out.WalletUserPort;

@Component
public class LoadWalletUserHandler extends AbstractWalletRequestHandler<CreateWalletContext> {

    private final WalletUserPort walletUserPort;

    public LoadWalletUserHandler(WalletUserPort walletUserPort) {
        this.walletUserPort = walletUserPort;
    }

    @Override
    protected void doHandle(CreateWalletContext context) {
        context.setUser(walletUserPort.requireUser(context.getUserId()));
    }
}
