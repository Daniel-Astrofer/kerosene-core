package source.ledger.application.transaction.handler;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import source.ledger.application.transaction.InternalTransferHistoryPort;
import source.ledger.application.transaction.TransactionContext;
import source.ledger.application.transaction.TransactionHandler;
import source.ledger.application.transaction.TransactionHandlerChain;

import java.time.LocalDateTime;

@Component
@Order(70)
public class TransactionHistoryHandler implements TransactionHandler {

    private final InternalTransferHistoryPort historyPort;

    public TransactionHistoryHandler(InternalTransferHistoryPort historyPort) {
        this.historyPort = historyPort;
    }

    @Override
    public void handle(TransactionContext context, TransactionHandlerChain chain) {
        historyPort.recordInternalTransfer(new InternalTransferHistoryPort.InternalTransferRecord(
                context.getTransaction().getAmount().abs(),
                LocalDateTime.now(),
                context.getEffectiveContext(),
                context.getSender().getId(),
                context.getSenderWallet().getName(),
                context.getReceiverWallet().getUser().getId(),
                context.getReceiverWallet().getName()));
        chain.next(context);
    }
}
