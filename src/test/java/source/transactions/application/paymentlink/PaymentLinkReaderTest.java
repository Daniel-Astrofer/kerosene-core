package source.transactions.application.paymentlink;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.transactions.dto.PaymentLinkDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentLinkReaderTest {

    @Mock
    private PaymentLinkStore paymentLinkStore;

    @InjectMocks
    private PaymentLinkReader paymentLinkReader;

    @Test
    void shouldExpirePendingLinkOnRead() {
        PaymentLinkDTO paymentLink = new PaymentLinkDTO();
        paymentLink.setId("pay-2");
        paymentLink.setAmountBtc(new BigDecimal("0.10000000"));
        paymentLink.setStatus(PaymentLinkStatus.PENDING);
        paymentLink.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(paymentLinkStore.findById("pay-2")).thenReturn(Optional.of(paymentLink));

        PaymentLinkDTO loaded = paymentLinkReader.getPaymentLink("pay-2");

        assertEquals(PaymentLinkStatus.EXPIRED, loaded.getStatus());
        assertEquals("EXPIRED", loaded.getPaymentIntentStatus());
        assertEquals(true, loaded.getTerminal());
        verify(paymentLinkStore).save(paymentLink);
    }

    @Test
    void shouldHideNonOnboardingLinkFromPublicLookup() {
        PaymentLinkDTO paymentLink = new PaymentLinkDTO();
        paymentLink.setId("pay-3");
        paymentLink.setDescription("regular");
        paymentLink.setStatus(PaymentLinkStatus.PENDING);
        paymentLink.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        when(paymentLinkStore.findById("pay-3")).thenReturn(Optional.of(paymentLink));

        assertNull(paymentLinkReader.getPublicOnboardingPaymentLink("pay-3"));
    }
}
