package source.transactions.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import source.auth.application.orchestrator.signup.SignupUseCase;
import source.transactions.dto.PaymentLinkDTO;
import source.transactions.infra.BlockchainClient;
import source.treasury.service.FinancialIntegrityService;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class OnboardingMonitorServiceTest {

    @Mock
    private RedisTemplate<String, PaymentLinkDTO> redisTemplate;
    @Mock
    private ValueOperations<String, PaymentLinkDTO> valueOperations;
    @Mock
    private SignupUseCase signupUseCase;
    @Mock
    private FinancialIntegrityService financialIntegrityService;
    @Mock
    private BlockchainClient blockchainClient;

    @InjectMocks
    private OnboardingMonitorService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(blockchainClient.getTransactionConfirmations("tx-abc")).thenReturn(3);
    }

    @Test
    void shouldSetRedisTTLTo1HourAfterCompletion() {
        PaymentLinkDTO dto = new PaymentLinkDTO();
        dto.setId("link-123");
        dto.setStatus("verifying_onboarding");
        dto.setTxid("tx-abc");
        dto.setSessionId("session-xyz");
        dto.setAmountBtc(new BigDecimal("0.001"));

        // Invoke private checkConfirmations
        try {
            java.lang.reflect.Method method = OnboardingMonitorService.class.getDeclaredMethod("checkConfirmations", PaymentLinkDTO.class, String.class);
            method.setAccessible(true);
            method.invoke(service, dto, "payment_link:link-123");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Verify signup was finalized
        verify(signupUseCase).finalizeUserFromRedis("session-xyz", "tx-abc", new BigDecimal("0.001"));

        // Verify TTL was set to 1 hour
        ArgumentCaptor<Long> timeoutCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<TimeUnit> unitCaptor = ArgumentCaptor.forClass(TimeUnit.class);

        verify(valueOperations).set(eq("payment_link:link-123"), any(PaymentLinkDTO.class), timeoutCaptor.capture(), unitCaptor.capture());

        assertEquals(1L, timeoutCaptor.getValue());
        assertEquals(TimeUnit.HOURS, unitCaptor.getValue());
    }
}
