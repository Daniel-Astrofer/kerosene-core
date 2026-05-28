package source.transactions.exception;

public final class TransactionExceptions {

    private TransactionExceptions() {
    }

    public static class TransactionBroadcastFailed extends RuntimeException {
        public TransactionBroadcastFailed(String message) {
            super(message);
        }
    }

    public static class TransactionBuildFailed extends RuntimeException {
        public TransactionBuildFailed(String message) {
            super(message);
        }

        public TransactionBuildFailed(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
