package source.payments.service;

import org.junit.jupiter.api.Test;
import source.payments.exception.PaymentException;
import source.payments.model.PaymentEnums;
import source.payments.model.PaymentIntentEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentStateMachineTest {

    private final PaymentStateMachine stateMachine = new PaymentStateMachine();

    @Test
    void allowsExternalHappyPathTransitions() {
        PaymentIntentEntity intent = new PaymentIntentEntity();

        stateMachine.quote(intent);
        stateMachine.confirm(intent);
        stateMachine.startProcessing(intent);
        stateMachine.acceptByProvider(intent);
        stateMachine.settle(intent);

        assertEquals(PaymentEnums.PaymentIntentStatus.SETTLED, intent.getStatus());
        assertTrue(stateMachine.isTerminal(intent));
        assertNull(intent.getFailureCode());
        assertNull(intent.getFailureMessage());
    }

    @Test
    void movesProcessingIntentToReconciliationWithFailureMetadata() {
        PaymentIntentEntity intent = new PaymentIntentEntity();
        intent.setStatus(PaymentEnums.PaymentIntentStatus.PROCESSING);

        stateMachine.requireReconciliation(intent, "PAYMENT_UNKNOWN", "Provider status is unknown.");

        assertEquals(PaymentEnums.PaymentIntentStatus.REQUIRES_RECONCILIATION, intent.getStatus());
        assertEquals("PAYMENT_UNKNOWN", intent.getFailureCode());
        assertEquals("Provider status is unknown.", intent.getFailureMessage());
        assertTrue(stateMachine.isInFlight(intent));
    }

    @Test
    void rejectsInvalidTransition() {
        PaymentIntentEntity intent = new PaymentIntentEntity();
        intent.setStatus(PaymentEnums.PaymentIntentStatus.QUOTED);

        PaymentException exception = assertThrows(PaymentException.class, () -> stateMachine.settle(intent));

        assertEquals("PAYMENT_STATUS_TRANSITION_INVALID", exception.getErrorCode());
        assertEquals(PaymentEnums.PaymentIntentStatus.QUOTED, intent.getStatus());
    }

    @Test
    void terminalStateCannotBeReopened() {
        PaymentIntentEntity intent = new PaymentIntentEntity();
        intent.setStatus(PaymentEnums.PaymentIntentStatus.SETTLED);

        PaymentException exception = assertThrows(PaymentException.class, () -> stateMachine.startProcessing(intent));

        assertEquals("PAYMENT_STATUS_TRANSITION_INVALID", exception.getErrorCode());
        assertEquals(PaymentEnums.PaymentIntentStatus.SETTLED, intent.getStatus());
    }
}
