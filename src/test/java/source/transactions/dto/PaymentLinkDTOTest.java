package source.transactions.dto;

import org.junit.jupiter.api.Test;
import source.transactions.application.paymentlink.PaymentLinkStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentLinkDTOTest {

    @Test
    void mapsPendingPaymentLinkToQuotedIntentCompatibility() {
        PaymentLinkDTO paymentLink = paymentLink("pay-compat-1", PaymentLinkStatus.PENDING);

        assertEquals("ONCHAIN", paymentLink.getPaymentRail());
        assertEquals("QUOTED", paymentLink.getPaymentIntentStatus());
        assertEquals("payment-link:pay-compat-1", paymentLink.getSettlementReference());
        assertEquals(false, paymentLink.getTerminal());
    }

    @Test
    void mapsTerminalPaymentLinkStatusesToPaymentIntentStatuses() {
        PaymentLinkDTO paid = paymentLink("pay-paid", PaymentLinkStatus.PAID);
        paid.setTxid("tx-paid");
        PaymentLinkDTO completed = paymentLink("pay-completed", PaymentLinkStatus.COMPLETED);
        PaymentLinkDTO expired = paymentLink("pay-expired", PaymentLinkStatus.EXPIRED);
        PaymentLinkDTO cancelled = paymentLink("pay-cancelled", PaymentLinkStatus.CANCELLED);

        assertEquals("SETTLED", paid.getPaymentIntentStatus());
        assertEquals("tx-paid", paid.getSettlementReference());
        assertEquals(true, paid.getTerminal());
        assertEquals("SETTLED", completed.getPaymentIntentStatus());
        assertEquals(true, completed.getTerminal());
        assertEquals("EXPIRED", expired.getPaymentIntentStatus());
        assertEquals(true, expired.getTerminal());
        assertEquals("CANCELED", cancelled.getPaymentIntentStatus());
        assertEquals(true, cancelled.getTerminal());
    }

    @Test
    void mapsVerificationStatusesToProcessingCompatibility() {
        PaymentLinkDTO onboarding = paymentLink("pay-onboarding", PaymentLinkStatus.VERIFYING_ONBOARDING);
        PaymentLinkDTO activation = paymentLink("pay-activation", PaymentLinkStatus.VERIFYING_ACTIVATION);

        assertEquals("PROCESSING", onboarding.getPaymentIntentStatus());
        assertEquals(false, onboarding.getTerminal());
        assertEquals("PROCESSING", activation.getPaymentIntentStatus());
        assertEquals(false, activation.getTerminal());
    }

    private PaymentLinkDTO paymentLink(String id, String status) {
        PaymentLinkDTO paymentLink = new PaymentLinkDTO();
        paymentLink.setId(id);
        paymentLink.setStatus(status);
        return paymentLink;
    }
}
