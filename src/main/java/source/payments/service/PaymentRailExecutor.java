package source.payments.service;

import source.payments.model.PaymentEnums;
import source.payments.model.PaymentIntentEntity;

public interface PaymentRailExecutor {

    enum ExecutionOutcome {
        ACCEPTED,
        SETTLED,
        FAILED_RETRYABLE,
        FAILED_FINAL,
        UNKNOWN
    }

    PaymentEnums.PaymentRail rail();

    ExecutionResult execute(PaymentIntentEntity intent);

    record ExecutionResult(
            ExecutionOutcome outcome,
            String providerReference,
            String providerStatus,
            String failureCode,
            String safeFailureMessage) {

        public ExecutionResult(String providerReference) {
            this(ExecutionOutcome.ACCEPTED, providerReference, null, null, null);
        }

        public static ExecutionResult accepted(String providerReference, String providerStatus) {
            return new ExecutionResult(ExecutionOutcome.ACCEPTED, providerReference, providerStatus, null, null);
        }

        public static ExecutionResult settled(String providerReference, String providerStatus) {
            return new ExecutionResult(ExecutionOutcome.SETTLED, providerReference, providerStatus, null, null);
        }

        public static ExecutionResult retryableFailure(String failureCode, String safeFailureMessage) {
            return new ExecutionResult(ExecutionOutcome.FAILED_RETRYABLE, null, null, failureCode, safeFailureMessage);
        }

        public static ExecutionResult finalFailure(String failureCode, String safeFailureMessage) {
            return new ExecutionResult(ExecutionOutcome.FAILED_FINAL, null, null, failureCode, safeFailureMessage);
        }

        public static ExecutionResult unknown(String providerReference, String providerStatus) {
            return new ExecutionResult(ExecutionOutcome.UNKNOWN, providerReference, providerStatus, null, null);
        }

        public ExecutionOutcome outcome() {
            return outcome != null ? outcome : ExecutionOutcome.ACCEPTED;
        }
    }
}
