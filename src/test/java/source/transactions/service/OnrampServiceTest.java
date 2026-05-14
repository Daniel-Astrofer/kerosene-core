package source.transactions.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletService;
import source.common.service.AddressDerivationService;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OnrampServiceTest {

    private OnrampService onrampService;

    @Mock
    private WalletService walletService;

    @Mock
    private AddressDerivationService addressDerivationService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        onrampService = new OnrampService(walletService, addressDerivationService, redisTemplate);

        ReflectionTestUtils.setField(onrampService, "moonpayBaseUrl", "https://buy.moonpay.com");
        ReflectionTestUtils.setField(onrampService, "banxaBaseUrl", "https://checkout.banxa.com");
        ReflectionTestUtils.setField(onrampService, "bipaBaseUrl", "https://bipa.app/buy/btc");
    }

    @Test
    public void testGenerateOnrampUrls() {
        Long userId = 1L;
        String mockAddress = "bc1qtestaddress";

        WalletEntity mockWallet = new WalletEntity();
        mockWallet.setDepositAddress(mockAddress);

        when(walletService.findByUserId(userId)).thenReturn(Collections.singletonList(mockWallet));
        when(valueOperations.get(anyString())).thenReturn(null);

        Map<String, String> urls = onrampService.generateOnrampUrls(userId);

        assertNotNull(urls);
        assertTrue(urls.containsKey("moonpay"));
        assertTrue(urls.containsKey("banxa"));
        assertTrue(urls.containsKey("bipa"));

        assertTrue(urls.get("moonpay").contains("currencyCode=btc&walletAddress=" + mockAddress));
        assertTrue(urls.get("banxa").contains("walletAddress=" + mockAddress));
        assertTrue(urls.get("bipa").contains("address=" + mockAddress));
    }

    @Test
    public void testGenerateOnrampUrlsNoWallet() {
        Long userId = 1L;
        when(walletService.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> {
            onrampService.generateOnrampUrls(userId);
        });
    }
}
