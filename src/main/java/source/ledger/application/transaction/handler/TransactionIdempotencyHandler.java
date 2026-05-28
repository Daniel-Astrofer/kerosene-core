package source.ledger.application.transaction.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import source.common.infra.logging.LogSanitizer;
import source.ledger.application.transaction.TransactionContext;
import source.ledger.application.transaction.TransactionHandler;
import source.ledger.application.transaction.TransactionHandlerChain;
import source.ledger.application.transaction.TransactionIdempotencyPort;
import source.common.observability.FinancialOperationsMetrics;
import source.ledger.dto.TransactionDTO;
import source.ledger.exceptions.LedgerExceptions;

import java.util.concurrent.TimeUnit;

@Component
@Order(40)
public class TransactionIdempotencyHandler implements TransactionHandler {

    private static final Logger log = LoggerFactory.getLogger(TransactionIdempotencyHandler.class);

    private static final String IDEMPOTENCY_PREFIX = "tx_idem:";
    private static final long IDEMPOTENCY_TTL_MINUTES = 10L;

    private final TransactionIdempotencyPort transactionIdempotencyPort;
    private final FinancialOperationsMetrics financialMetrics;

    public TransactionIdempotencyHandler(
            TransactionIdempotencyPort transactionIdempotencyPort,
            FinancialOperationsMetrics financialMetrics) {
        this.transactionIdempotencyPort = transactionIdempotencyPort;
        this.financialMetrics = financialMetrics;
    }

    @Override
    public void handle(TransactionContext context, TransactionHandlerChain chain) {
        TransactionDTO transaction = context.getTransaction();
        if (transaction.getIdempotencyKey() == null || transaction.getIdempotencyKey().isBlank()) {
            financialMetrics.increment("idempotency_rejected", "missing", "ledger");
            throw new LedgerExceptions.TransactionReplayException(
                    "idempotencyKey is required for financial transactions.");
        }

        String key = IDEMPOTENCY_PREFIX + transaction.getIdempotencyKey();
        boolean reserved = transactionIdempotencyPort.reserve(key, IDEMPOTENCY_TTL_MINUTES, TimeUnit.MINUTES);
        if (!reserved) {
            financialMetrics.increment("idempotency_reused", "duplicate", "ledger");
            log.warn("TX REJECTED - duplicate idempotencyKeyRef={}",
                    LogSanitizer.fingerprint(transaction.getIdempotencyKey()));
            throw new LedgerExceptions.DuplicateTransactionException(
                    "This transaction has already been submitted (duplicate idempotency key). "
                            + "If the previous attempt failed, generate a new idempotency key and retry.");
        }

        chain.next(context);
    }
}
