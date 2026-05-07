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

    public static class ReceiverNotFoundException extends LedgerException {
        public ReceiverNotFoundException(String message) {
            super(message);
        }
    }

    public static class ReceiverNotReadyException extends LedgerException {
        public enum Reason {
            NO_RECEIVING_WALLET,
            INBOUND_BLOCKED
        }

        private final Reason reason;

        public ReceiverNotReadyException(String message, Reason reason) {
            super(message);
            this.reason = reason;
        }

        public Reason getReason() {
            return reason;
        }

        public static ReceiverNotReadyException noReceivingWallet() {
            return new ReceiverNotReadyException(
                    "The destination user exists but does not yet have a wallet ready to receive funds.",
                    Reason.NO_RECEIVING_WALLET);
        }

        public static ReceiverNotReadyException inboundBlocked() {
            return new ReceiverNotReadyException(
                    "The destination user exists but is not yet ready to receive funds.",
                    Reason.INBOUND_BLOCKED);
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

    public static class PaymentRequestNotFoundException extends LedgerException {
        public PaymentRequestNotFoundException(String message) {
            super(message);
        }
    }

    public static class PaymentRequestExpiredException extends LedgerException {
        public PaymentRequestExpiredException(String message) {
            super(message);
        }
    }

    public static class PaymentRequestAlreadyPaidException extends LedgerException {
        public PaymentRequestAlreadyPaidException(String message) {
            super(message);
        }
    }

    public static class PaymentRequestSelfPayException extends LedgerException {
        public PaymentRequestSelfPayException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when a request arrives with an idempotencyKey that was already
     * processed. The caller should treat this as a successful no-op (HTTP 409
     * or idempotent 200 depending on API contract), NOT as an error to retry.
     */
    public static class DuplicateTransactionException extends LedgerException {
        public DuplicateTransactionException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when a request's timestamp falls outside the allowed window.
     * This protects against replay attacks where an old signed request is
     * re-sent by a malicious relay node.
     */
    public static class TransactionReplayException extends LedgerException {
        public TransactionReplayException(String message) {
            super(message);
        }
    }

    public static class LedgerSyncException extends LedgerException {
        public LedgerSyncException(String message) {
            super(message);
        }
    }

    public static class LedgerIntegrityViolationException extends LedgerException {
        public LedgerIntegrityViolationException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when any financial operation receives an invalid amount (zero,
     * negative, or null).
     */
    public static class InvalidAmountException extends LedgerException {
        public InvalidAmountException(String message) {
            super(message);
        }
    }
}
