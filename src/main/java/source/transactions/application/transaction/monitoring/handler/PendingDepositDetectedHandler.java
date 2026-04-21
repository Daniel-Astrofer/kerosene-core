package source.transactions.application.transaction.monitoring.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import source.transactions.application.transaction.monitoring.PendingTransactionMonitorContext;
import source.transactions.application.transaction.monitoring.PendingTransactionMonitorHandler;
import source.transactions.application.transaction.monitoring.PendingTransactionMonitorHandlerChain;
import source.transactions.application.transaction.monitoring.PendingTransactionObservationPort;

@Component
@Order(30)
public class PendingDepositDetectedHandler implements PendingTransactionMonitorHandler {

    private static final Logger log = LoggerFactory.getLogger(PendingDepositDetectedHandler.class);

    private final PendingTransactionObservationPort pendingTransactionObservationPort;

    public PendingDepositDetectedHandler(PendingTransactionObservationPort pendingTransactionObservationPort) {
        this.pendingTransactionObservationPort = pendingTransactionObservationPort;
    }

    @Override
    public void handle(PendingTransactionMonitorContext context, PendingTransactionMonitorHandlerChain chain) {
        if (context.getConfirmations() == 0 && context.getTransaction().getConfirmations() == null) {
            try {
                pendingTransactionObservationPort.notifyPendingDepositDetected(context.getTransaction());
            } catch (RuntimeException ex) {
                log.error("Erro ao notificar depósito pendente: {}", ex.getMessage());
            }
        }

        chain.next(context);
    }
}
