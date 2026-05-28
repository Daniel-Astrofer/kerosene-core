package source.payments.service;

import source.payments.model.PaymentEnums;
import source.payments.model.PaymentExecutionOutboxEntity;
import source.payments.model.PaymentIntentEntity;

public interface PaymentRailStatusClient {

    PaymentEnums.PaymentRail rail();

    StatusResult status(PaymentIntentEntity intent, PaymentExecutionOutboxEntity outbox);

    record StatusResult(
            PaymentRailExecutor.ExecutionOutcome outcome,
            String providerReference,
            String providerStatus,
            String failureCode,
            String safeFailureMessage) {

        public PaymentRailExecutor.ExecutionOutcome outcome() {
            return outcome != null ? outcome : PaymentRailExecutor.ExecutionOutcome.UNKNOWN;
        }

        public static StatusResult accepted(String providerReference, String providerStatus) {
            return new StatusResult(
                    PaymentRailExecutor.ExecutionOutcome.ACCEPTED,
                    providerReference,
                    providerStatus,
                    null,
                    null);
        }

        public static StatusResult settled(String providerReference, String providerStatus) {
            return new StatusResult(
                    PaymentRailExecutor.ExecutionOutcome.SETTLED,
                    providerReference,
                    providerStatus,
                    null,
                    null);
        }

        public static StatusResult retryableFailure(String failureCode, String safeFailureMessage) {
            return new StatusResult(
                    PaymentRailExecutor.ExecutionOutcome.FAILED_RETRYABLE,
                    null,
                    null,
                    failureCode,
                    safeFailureMessage);
        }

        public static StatusResult finalFailure(String failureCode, String safeFailureMessage) {
            return new StatusResult(
                    PaymentRailExecutor.ExecutionOutcome.FAILED_FINAL,
                    null,
                    null,
                    failureCode,
                    safeFailureMessage);
        }

        public static StatusResult unknown(String providerReference, String providerStatus) {
            return new StatusResult(
                    PaymentRailExecutor.ExecutionOutcome.UNKNOWN,
                    providerReference,
                    providerStatus,
                    null,
                    null);
        }
    }
}
