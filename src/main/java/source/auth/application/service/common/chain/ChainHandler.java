package source.auth.application.service.common.chain;

public interface ChainHandler<T> {

    void setNext(ChainHandler<T> next);

    void handle(T context);
}
