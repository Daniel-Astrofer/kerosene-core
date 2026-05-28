package source.auth.application.service.common.chain;

public abstract class AbstractChainHandler<T> implements ChainHandler<T> {

    private ChainHandler<T> next;

    @Override
    public final void setNext(ChainHandler<T> next) {
        this.next = next;
    }

    protected final void handleNext(T context) {
        if (next != null) {
            next.handle(context);
        }
    }
}
