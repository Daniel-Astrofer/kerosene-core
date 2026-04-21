package source.transactions.application.transaction.monitoring;

public class TransactionMonitoringRateLimitException extends RuntimeException {

    public TransactionMonitoringRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
