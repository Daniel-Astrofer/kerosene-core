package source.treasury.application.chain;

public interface ChainHandler<C extends ChainContext> {

    void handle(C context);

    ChainHandler<C> linkWith(ChainHandler<C> next);
}
