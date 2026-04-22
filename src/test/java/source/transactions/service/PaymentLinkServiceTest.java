package source.transactions.service;

import org.junit.jupiter.api.Test;
import source.transactions.application.paymentlink.PaymentLinkCompleter;
import source.transactions.application.paymentlink.PaymentLinkConfirmer;
import source.transactions.application.paymentlink.PaymentLinkCreator;
import source.transactions.application.paymentlink.PaymentLinkDescription;
import source.transactions.application.paymentlink.PaymentLinkReader;
import source.transactions.application.paymentlink.PaymentLinkStatus;
import source.transactions.application.paymentlink.PaymentLinkStore;
import source.transactions.dto.PaymentLinkDTO;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentLinkServiceTest {

    @Test
    void shouldFinalizePublicOnboardingPaymentImmediatelyWhenVoucherMockModeIsEnabled() {
        PaymentLinkCreator creator = mock(PaymentLinkCreator.class);
        PaymentLinkReader reader = mock(PaymentLinkReader.class);
        PaymentLinkConfirmer confirmer = mock(PaymentLinkConfirmer.class);
        PaymentLinkCompleter completer = mock(PaymentLinkCompleter.class);
        PaymentLinkStore store = mock(PaymentLinkStore.class);
        OnboardingPaymentFinalizer finalizer = mock(OnboardingPaymentFinalizer.class);
        PaymentLinkService service = new PaymentLinkService(
                creator,
                reader,
                confirmer,
                completer,
                store,
                finalizer,
                true);

        PaymentLinkDTO pending = onboardingLink(PaymentLinkStatus.PENDING);
        PaymentLinkDTO verifying = onboardingLink(PaymentLinkStatus.VERIFYING_ONBOARDING);
        verifying.setTxid("qualquer-txid");
        PaymentLinkDTO completed = onboardingLink(PaymentLinkStatus.COMPLETED);
        completed.setTxid("qualquer-txid");

        when(reader.getPublicOnboardingPaymentLink("pay-1")).thenReturn(pending);
        when(confirmer.confirmPayment("pay-1", "qualquer-txid", null)).thenReturn(verifying);
        when(reader.isOnboardingPaymentLink(verifying)).thenReturn(true);
        when(finalizer.finalizeConfirmedPayment(verifying)).thenReturn(completed);

        PaymentLinkDTO result = service.confirmPublicOnboardingPayment("pay-1", "qualquer-txid", null);

        assertEquals(PaymentLinkStatus.COMPLETED, result.getStatus());
        verify(finalizer).finalizeConfirmedPayment(verifying);
    }

    @Test
    void shouldKeepOnboardingPaymentVerifyingWhenVoucherMockModeIsDisabled() {
        PaymentLinkCreator creator = mock(PaymentLinkCreator.class);
        PaymentLinkReader reader = mock(PaymentLinkReader.class);
        PaymentLinkConfirmer confirmer = mock(PaymentLinkConfirmer.class);
        PaymentLinkCompleter completer = mock(PaymentLinkCompleter.class);
        PaymentLinkStore store = mock(PaymentLinkStore.class);
        OnboardingPaymentFinalizer finalizer = mock(OnboardingPaymentFinalizer.class);
        PaymentLinkService service = new PaymentLinkService(
                creator,
                reader,
                confirmer,
                completer,
                store,
                finalizer,
                false);

        PaymentLinkDTO pending = onboardingLink(PaymentLinkStatus.PENDING);
        PaymentLinkDTO verifying = onboardingLink(PaymentLinkStatus.VERIFYING_ONBOARDING);
        verifying.setTxid("real-txid");

        when(reader.getPublicOnboardingPaymentLink("pay-1")).thenReturn(pending);
        when(confirmer.confirmPayment("pay-1", "real-txid", null)).thenReturn(verifying);

        PaymentLinkDTO result = service.confirmPublicOnboardingPayment("pay-1", "real-txid", null);

        assertEquals(PaymentLinkStatus.VERIFYING_ONBOARDING, result.getStatus());
    }

    @Test
    void shouldFinalizeExistingVerifyingOnboardingPaymentOnPublicReadWhenVoucherMockModeIsEnabled() {
        PaymentLinkCreator creator = mock(PaymentLinkCreator.class);
        PaymentLinkReader reader = mock(PaymentLinkReader.class);
        PaymentLinkConfirmer confirmer = mock(PaymentLinkConfirmer.class);
        PaymentLinkCompleter completer = mock(PaymentLinkCompleter.class);
        PaymentLinkStore store = mock(PaymentLinkStore.class);
        OnboardingPaymentFinalizer finalizer = mock(OnboardingPaymentFinalizer.class);
        PaymentLinkService service = new PaymentLinkService(
                creator,
                reader,
                confirmer,
                completer,
                store,
                finalizer,
                true);

        PaymentLinkDTO verifying = onboardingLink(PaymentLinkStatus.VERIFYING_ONBOARDING);
        verifying.setTxid("txid-ja-enviado");
        PaymentLinkDTO completed = onboardingLink(PaymentLinkStatus.COMPLETED);
        completed.setTxid("txid-ja-enviado");

        when(reader.getPublicOnboardingPaymentLink("pay-1")).thenReturn(verifying);
        when(reader.isOnboardingPaymentLink(verifying)).thenReturn(true);
        when(finalizer.finalizeConfirmedPayment(verifying)).thenReturn(completed);

        PaymentLinkDTO result = service.getPublicOnboardingPaymentLink("pay-1");

        assertEquals(PaymentLinkStatus.COMPLETED, result.getStatus());
        verify(finalizer).finalizeConfirmedPayment(verifying);
    }

    private PaymentLinkDTO onboardingLink(String status) {
        PaymentLinkDTO paymentLink = new PaymentLinkDTO();
        paymentLink.setId("pay-1");
        paymentLink.setSessionId("session-1");
        paymentLink.setAmountBtc(new BigDecimal("0.00022000"));
        paymentLink.setDepositAddress("tb1qmock");
        paymentLink.setDescription(PaymentLinkDescription.ONBOARDING_VOUCHER);
        paymentLink.setStatus(status);
        return paymentLink;
    }
}
