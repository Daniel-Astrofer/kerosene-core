package source.transactions.service;

import org.springframework.stereotype.Service;
import source.auth.application.orchestrator.signup.FinalizeSignupOnPayment;
import source.transactions.application.paymentlink.PaymentLinkStatus;
import source.transactions.application.paymentlink.PaymentLinkStore;
import source.transactions.dto.PaymentLinkDTO;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class OnboardingPaymentFinalizer {

    private final PaymentLinkStore paymentLinkStore;
    private final FinalizeSignupOnPayment finalizeSignupOnPayment;

    public OnboardingPaymentFinalizer(
            PaymentLinkStore paymentLinkStore,
            FinalizeSignupOnPayment finalizeSignupOnPayment) {
        this.paymentLinkStore = paymentLinkStore;
        this.finalizeSignupOnPayment = finalizeSignupOnPayment;
    }

    public PaymentLinkDTO finalizeConfirmedPayment(PaymentLinkDTO paymentLink) {
        if (paymentLink == null) {
            throw new IllegalArgumentException("Onboarding payment link is required.");
        }
        if (paymentLink.getSessionId() == null || paymentLink.getSessionId().isBlank()) {
            throw new IllegalStateException("Onboarding session is missing for payment link " + paymentLink.getId());
        }
        if (paymentLink.getTxid() == null || paymentLink.getTxid().isBlank()) {
            throw new IllegalStateException("Onboarding txid is missing for payment link " + paymentLink.getId());
        }

        boolean finalized = finalizeSignupOnPayment.execute(
                paymentLink.getSessionId(),
                paymentLink.getTxid(),
                paymentLink.getAmountBtc());
        if (!finalized) {
            throw new IllegalStateException(
                    "Onboarding state is incomplete or no longer available for finalization.");
        }

        paymentLink.setStatus(PaymentLinkStatus.COMPLETED);
        paymentLink.setCompletedAt(LocalDateTime.now());
        return paymentLinkStore.save(paymentLink, Duration.ofHours(24));
    }
}
