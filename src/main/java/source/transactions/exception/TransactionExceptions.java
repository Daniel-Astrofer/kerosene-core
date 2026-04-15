package source.transactions.exception;

public final class TransactionExceptions {

    private TransactionExceptions() {
    }

    public static class TransactionBroadcastFailed extends RuntimeException {
        public TransactionBroadcastFailed(String message) {
            super(message);
        }
    }
}
