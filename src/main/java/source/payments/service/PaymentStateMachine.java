package source.payments.service;

import org.springframework.stereotype.Service;
import source.payments.exception.PaymentException;
import source.payments.model.PaymentEnums;
import source.payments.model.PaymentIntentEntity;

import java.util.EnumSet;
import java.util.Objects;

@Service
public class PaymentStateMachine {

    public PaymentIntentEntity quote(PaymentIntentEntity intent) {
        return transition(intent, PaymentEnums.PaymentIntentStatus.QUOTED, null, null);
    }

    public PaymentIntentEntity confirm(PaymentIntentEntity intent) {
        return transition(intent, PaymentEnums.PaymentIntentStatus.CONFIRMED, null, null);
    }

    public PaymentIntentEntity startProcessing(PaymentIntentEntity intent) {
        return transition(intent, PaymentEnums.PaymentIntentStatus.PROCESSING, null, null);
    }

    public PaymentIntentEntity acceptByProvider(PaymentIntentEntity intent) {
        return transition(intent, PaymentEnums.PaymentIntentStatus.ACCEPTED_BY_PROVIDER, null, null);
    }

    public PaymentIntentEntity settle(PaymentIntentEntity intent) {
        return transition(intent, PaymentEnums.PaymentIntentStatus.SETTLED, null, null);
    }

    public PaymentIntentEntity expire(PaymentIntentEntity intent, String failureCode, String failureMessage) {
        return transition(intent, PaymentEnums.PaymentIntentStatus.EXPIRED, failureCode, failureMessage);
    }

    public PaymentIntentEntity requireReconciliation(
            PaymentIntentEntity intent,
            String failureCode,
            String failureMessage) {
        return transition(
                intent,
                PaymentEnums.PaymentIntentStatus.REQUIRES_RECONCILIATION,
                failureCode,
                failureMessage);
    }

    public PaymentIntentEntity fail(PaymentIntentEntity intent, String failureCode, String failureMessage) {
        return transition(intent, PaymentEnums.PaymentIntentStatus.FAILED, failureCode, failureMessage);
    }

    public boolean isTerminal(PaymentIntentEntity intent) {
        PaymentEnums.PaymentIntentStatus status = statusOf(intent);
        return status == PaymentEnums.PaymentIntentStatus.SETTLED
                || status == PaymentEnums.PaymentIntentStatus.FAILED
                || status == PaymentEnums.PaymentIntentStatus.CANCELED
                || status == PaymentEnums.PaymentIntentStatus.EXPIRED;
    }

    public boolean isInFlight(PaymentIntentEntity intent) {
        PaymentEnums.PaymentIntentStatus status = statusOf(intent);
        return status == PaymentEnums.PaymentIntentStatus.PROCESSING
                || status == PaymentEnums.PaymentIntentStatus.ACCEPTED_BY_PROVIDER
                || status == PaymentEnums.PaymentIntentStatus.REQUIRES_RECONCILIATION;
    }

    public PaymentIntentEntity transition(
            PaymentIntentEntity intent,
            PaymentEnums.PaymentIntentStatus target,
            String failureCode,
            String failureMessage) {
        Objects.requireNonNull(intent, "payment intent is required");
        Objects.requireNonNull(target, "target status is required");

        PaymentEnums.PaymentIntentStatus current = statusOf(intent);
        if (current != target && !allowedTargets(current).contains(target)) {
            throw PaymentException.conflict(
                    "PAYMENT_STATUS_TRANSITION_INVALID",
                    "Este envio nao pode mudar de " + current + " para " + target + ".");
        }

        intent.setStatus(target);
        applyFailureMetadata(intent, target, failureCode, failureMessage);
        return intent;
    }

    private PaymentEnums.PaymentIntentStatus statusOf(PaymentIntentEntity intent) {
        if (intent == null || intent.getStatus() == null) {
            return PaymentEnums.PaymentIntentStatus.CREATED;
        }
        return intent.getStatus();
    }

    private EnumSet<PaymentEnums.PaymentIntentStatus> allowedTargets(PaymentEnums.PaymentIntentStatus current) {
        return switch (current) {
            case CREATED -> EnumSet.of(
                    PaymentEnums.PaymentIntentStatus.QUOTED,
                    PaymentEnums.PaymentIntentStatus.CANCELED,
                    PaymentEnums.PaymentIntentStatus.FAILED);
            case QUOTED -> EnumSet.of(
                    PaymentEnums.PaymentIntentStatus.CONFIRMED,
                    PaymentEnums.PaymentIntentStatus.EXPIRED,
                    PaymentEnums.PaymentIntentStatus.CANCELED,
                    PaymentEnums.PaymentIntentStatus.FAILED);
            case CONFIRMED -> EnumSet.of(
                    PaymentEnums.PaymentIntentStatus.PROCESSING,
                    PaymentEnums.PaymentIntentStatus.CANCELED,
                    PaymentEnums.PaymentIntentStatus.FAILED);
            case PROCESSING -> EnumSet.of(
                    PaymentEnums.PaymentIntentStatus.ACCEPTED_BY_PROVIDER,
                    PaymentEnums.PaymentIntentStatus.REQUIRES_RECONCILIATION,
                    PaymentEnums.PaymentIntentStatus.SETTLED,
                    PaymentEnums.PaymentIntentStatus.FAILED);
            case ACCEPTED_BY_PROVIDER -> EnumSet.of(
                    PaymentEnums.PaymentIntentStatus.REQUIRES_RECONCILIATION,
                    PaymentEnums.PaymentIntentStatus.SETTLED,
                    PaymentEnums.PaymentIntentStatus.FAILED);
            case REQUIRES_RECONCILIATION -> EnumSet.of(
                    PaymentEnums.PaymentIntentStatus.SETTLED,
                    PaymentEnums.PaymentIntentStatus.FAILED);
            case SETTLED, FAILED, CANCELED, EXPIRED -> EnumSet.noneOf(PaymentEnums.PaymentIntentStatus.class);
        };
    }

    private void applyFailureMetadata(
            PaymentIntentEntity intent,
            PaymentEnums.PaymentIntentStatus target,
            String failureCode,
            String failureMessage) {
        if (target == PaymentEnums.PaymentIntentStatus.FAILED
                || target == PaymentEnums.PaymentIntentStatus.EXPIRED
                || target == PaymentEnums.PaymentIntentStatus.REQUIRES_RECONCILIATION) {
            intent.setFailureCode(firstNonBlank(failureCode, target.name()));
            intent.setFailureMessage(firstNonBlank(failureMessage, "Payment status changed to " + target.name() + "."));
            return;
        }

        intent.setFailureCode(null);
        intent.setFailureMessage(null);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
