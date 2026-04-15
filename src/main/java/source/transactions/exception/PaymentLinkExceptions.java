package source.transactions.exception;

public final class PaymentLinkExceptions {

    private PaymentLinkExceptions() {
    }

    public static class PaymentLinkNotFound extends RuntimeException {
        public PaymentLinkNotFound(String message) {
            super(message);
        }
    }

    public static class PaymentLinkExpired extends RuntimeException {
        public PaymentLinkExpired(String message) {
            super(message);
        }
    }

    public static class InvalidPaymentLinkState extends RuntimeException {
        public InvalidPaymentLinkState(String message) {
            super(message);
        }
    }

    public static class InvalidPaymentLinkTransaction extends RuntimeException {
        public InvalidPaymentLinkTransaction(String message) {
            super(message);
        }
    }

    public static class PaymentLinkCreditFailed extends RuntimeException {
        public PaymentLinkCreditFailed(String message) {
            super(message);
        }

        public PaymentLinkCreditFailed(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
