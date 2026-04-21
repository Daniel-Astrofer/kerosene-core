package source.treasury.application.chain;

public abstract class AbstractChainHandler<C extends ChainContext> implements ChainHandler<C> {

    private ChainHandler<C> next;

    @Override
    public final void handle(C context) {
        if (context.shouldStop()) {
            return;
        }

        doHandle(context);

        if (!context.shouldStop() && next != null) {
            next.handle(context);
        }
    }

    @Override
    public ChainHandler<C> linkWith(ChainHandler<C> next) {
        this.next = next;
        return next;
    }

    protected abstract void doHandle(C context);
}
