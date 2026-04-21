package source.wallet.application.chain;

public abstract class AbstractWalletRequestHandler<C> implements WalletRequestHandler<C> {

    private WalletRequestHandler<C> next;

    @Override
    public final void handle(C context) {
        doHandle(context);
        if (next != null) {
            next.handle(context);
        }
    }

    @Override
    public WalletRequestHandler<C> linkWith(WalletRequestHandler<C> next) {
        this.next = next;
        return next;
    }

    protected abstract void doHandle(C context);
}
