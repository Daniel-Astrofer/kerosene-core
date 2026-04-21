package source.transactions.application.transaction.monitoring;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PendingTransactionMonitorPipeline {

    private final List<PendingTransactionMonitorHandler> handlers;

    public PendingTransactionMonitorPipeline(List<PendingTransactionMonitorHandler> handlers) {
        this.handlers = List.copyOf(handlers);
    }

    public void execute(PendingTransactionMonitorContext context) {
        new DefaultChain(handlers, 0).next(context);
    }

    private static final class DefaultChain implements PendingTransactionMonitorHandlerChain {

        private final List<PendingTransactionMonitorHandler> handlers;
        private final int index;

        private DefaultChain(List<PendingTransactionMonitorHandler> handlers, int index) {
            this.handlers = handlers;
            this.index = index;
        }

        @Override
        public void next(PendingTransactionMonitorContext context) {
            if (context.shouldStop() || index >= handlers.size()) {
                return;
            }

            PendingTransactionMonitorHandler handler = handlers.get(index);
            handler.handle(context, new DefaultChain(handlers, index + 1));
        }
    }
}
