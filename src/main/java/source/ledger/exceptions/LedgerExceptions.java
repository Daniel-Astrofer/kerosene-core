package source.ledger.exceptions;


public class LedgerExceptions {

    public static class LedgerException extends RuntimeException {
        public LedgerException(String message) {
            super(message);
        }
    }

    public static class LedgerNotFoundException extends LedgerException {
        public LedgerNotFoundException(String message) {
            super(message);
        }
    }

    public static class LedgerAlreadyExistsException extends LedgerException {
        public LedgerAlreadyExistsException(String message) {
            super(message);
        }
    }

    public static class InsufficientBalanceException extends LedgerException {
        public InsufficientBalanceException(String message) {
            super(message);
        }
    }

    public static class InvalidLedgerOperationException extends LedgerException {
        public InvalidLedgerOperationException(String message) {
            super(message);
        }
    }
}
