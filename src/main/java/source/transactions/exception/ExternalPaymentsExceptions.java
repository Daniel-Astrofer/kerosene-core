package source.transactions.exception;

public final class ExternalPaymentsExceptions {

    private ExternalPaymentsExceptions() {
    }

    public static class CustodyProviderUnavailable extends RuntimeException {
        public CustodyProviderUnavailable(String message) {
            super(message);
        }
    }

    public static class InvalidNetworkAddress extends RuntimeException {
        public InvalidNetworkAddress(String message) {
            super(message);
        }
    }

    public static class TransferNotFound extends RuntimeException {
        public TransferNotFound(String message) {
            super(message);
        }
    }
}
