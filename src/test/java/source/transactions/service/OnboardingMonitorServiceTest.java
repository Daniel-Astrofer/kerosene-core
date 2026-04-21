package source.transactions.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import source.auth.application.orchestrator.signup.FinalizeSignupOnPayment;
import source.transactions.application.paymentlink.PaymentLinkStore;
import source.transactions.dto.PaymentLinkDTO;
import source.transactions.infra.BlockchainClient;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class OnboardingMonitorServiceTest {

    @Mock
    private FinalizeSignupOnPayment finalizeSignupOnPayment;
    @Mock
    private PaymentLinkStore paymentLinkStore;
    @Mock
    private BlockchainClient blockchainClient;

    private OnboardingMonitorService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new OnboardingMonitorService(paymentLinkStore, finalizeSignupOnPayment, blockchainClient, 3);
    }

    @Test
    void shouldStoreCompletedOnboardingLinkFor24Hours() {
        PaymentLinkDTO dto = new PaymentLinkDTO();
        dto.setId("link-123");
        dto.setStatus("verifying_onboarding");
        dto.setTxid("tx-abc");
        dto.setSessionId("session-xyz");
        dto.setAmountBtc(new BigDecimal("0.001"));
        when(blockchainClient.getRawTransaction("tx-abc", true))
                .thenReturn(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                        .put("confirmations", 3));
        when(finalizeSignupOnPayment.execute("session-xyz", "tx-abc", new BigDecimal("0.001")))
                .thenReturn(true);

        // Invoke private checkConfirmations
        try {
            java.lang.reflect.Method method = OnboardingMonitorService.class.getDeclaredMethod("checkConfirmations", PaymentLinkDTO.class);
            method.setAccessible(true);
            method.invoke(service, dto);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Verify signup was finalized
        verify(finalizeSignupOnPayment).execute("session-xyz", "tx-abc", new BigDecimal("0.001"));

        // Verify the completed link is extended in the store
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        verify(paymentLinkStore).save(any(PaymentLinkDTO.class), ttlCaptor.capture());

        assertEquals(Duration.ofHours(24), ttlCaptor.getValue());
    }
}
