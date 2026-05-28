package source.ledger.application.transaction.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import source.ledger.application.transaction.TransactionContext;
import source.ledger.application.transaction.TransactionHandler;
import source.ledger.application.transaction.TransactionHandlerChain;
import source.ledger.dto.TransactionDTO;
import source.ledger.exceptions.LedgerExceptions;

@Component
@Order(10)
public class TransactionTimestampValidationHandler implements TransactionHandler {

    private static final Logger log = LoggerFactory.getLogger(TransactionTimestampValidationHandler.class);

    private static final long MAX_REQUEST_AGE_MS = 2 * 60 * 1_000L;

    @Override
    public void handle(TransactionContext context, TransactionHandlerChain chain) {
        TransactionDTO transaction = context.getTransaction();
        if (transaction.getRequestTimestamp() != null) {
            long age = System.currentTimeMillis() - transaction.getRequestTimestamp();
            if (age > MAX_REQUEST_AGE_MS || age < -30_000L) {
                log.warn("TX REJECTED — timestamp out of window: age={}ms, key={}", age, transaction.getIdempotencyKey());
                throw new LedgerExceptions.TransactionReplayException(
                        "Request timestamp is out of the allowed window. Please retry with a fresh request.");
            }
        }

        chain.next(context);
    }
}
