package source.transactions.application.paymentlink;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.transactions.dto.PaymentLinkDTO;
import source.wallet.model.WalletEntity;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentLinkCreatorTest {

    @Mock
    private PaymentLinkStore paymentLinkStore;

    @Mock
    private PaymentLinkHistoryPort paymentLinkHistoryPort;

    @Mock
    private PaymentLinkWalletPort paymentLinkWalletPort;

    @Mock
    private PaymentLinkAddressAllocationPort addressAllocationPort;

    private PaymentLinkCreator paymentLinkCreator;

    @BeforeEach
    void setUp() {
        paymentLinkCreator = new PaymentLinkCreator(
                paymentLinkStore,
                paymentLinkHistoryPort,
                paymentLinkWalletPort,
                addressAllocationPort,
                "bc1qfallback",
                60);
    }

    @Test
    void createsDedicatedCustodialAddressForUserPaymentLinks() {
        WalletEntity wallet = new WalletEntity();
        wallet.setId(10L);
        wallet.setName("MAIN");

        when(paymentLinkWalletPort.findPrimaryWallet(7L)).thenReturn(wallet);
        when(addressAllocationPort.allocate(eq(7L), eq(wallet), any(), eq(true)))
                .thenReturn(new PaymentLinkAddressAllocationPort.Allocation(
                        "bc1quserlink",
                        "XPUB_INDEX_4",
                        "KEROSENE_LOCAL",
                        false));

        PaymentLinkDTO paymentLink = paymentLinkCreator.createForUser(7L, new BigDecimal("0.01500000"), "invoice");

        assertNotNull(paymentLink.getId());
        assertEquals("bc1quserlink", paymentLink.getDepositAddress());
        assertEquals(Long.valueOf(7L), paymentLink.getUserId());
        verify(paymentLinkStore).save(paymentLink);
        verify(paymentLinkHistoryPort).recordCreated(paymentLink);
    }

    @Test
    void keepsStaticAddressForOnboardingLinks() {
        PaymentLinkDTO paymentLink = paymentLinkCreator.createForOnboarding(
                "session-1",
                new BigDecimal("0.01000000"),
                PaymentLinkDescription.ONBOARDING_VOUCHER);

        assertEquals("bc1qfallback", paymentLink.getDepositAddress());
        assertEquals("session-1", paymentLink.getSessionId());
    }

    @Test
    void keepsStaticAddressForAccountActivationLinksWithoutWalletLookup() {
        PaymentLinkDTO paymentLink = paymentLinkCreator.createForAccountActivation(
                11L,
                new BigDecimal("0.00005000"));

        assertNotNull(paymentLink.getId());
        assertEquals("bc1qfallback", paymentLink.getDepositAddress());
        assertEquals(Long.valueOf(11L), paymentLink.getUserId());
        assertEquals(PaymentLinkDescription.ACCOUNT_ACTIVATION, paymentLink.getDescription());
        assertEquals(PaymentLinkStatus.PENDING, paymentLink.getStatus());
        verify(paymentLinkStore).save(paymentLink);
        verify(paymentLinkHistoryPort).recordCreated(paymentLink);
        verify(paymentLinkWalletPort, never()).findPrimaryWallet(11L);
        verify(addressAllocationPort, never()).allocate(any(), any(), any(), eq(true));
    }
}
