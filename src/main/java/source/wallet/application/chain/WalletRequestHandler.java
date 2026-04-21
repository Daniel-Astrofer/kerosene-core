package source.wallet.application.chain;

public interface WalletRequestHandler<C> {

    void handle(C context);

    WalletRequestHandler<C> linkWith(WalletRequestHandler<C> next);
}
