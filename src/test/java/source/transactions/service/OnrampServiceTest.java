package source.transactions.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import source.auth.model.entity.UserDataBase;
import source.transactions.application.externalpayments.ExternalPaymentsMath;
import source.transactions.application.externalpayments.ExternalTransferFactory;
import source.transactions.application.externalpayments.ExternalTransfersPort;
import source.transactions.service.CustodialAddressAllocator;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletService;

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
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ExternalTransfersPort externalTransfersPort;

    @Mock
    private CustodialAddressAllocator custodialAddressAllocator;

    @Mock
    private BlockchainAddressWatchService blockchainAddressWatchService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        onrampService = new OnrampService(
                walletService,
                redisTemplate,
                externalTransfersPort,
                new ExternalTransferFactory(new ExternalPaymentsMath("testnet")),
                new ExternalPaymentsMath("testnet"),
                custodialAddressAllocator,
                blockchainAddressWatchService);

        ReflectionTestUtils.setField(onrampService, "moonpayBaseUrl", "https://buy.moonpay.com");
        ReflectionTestUtils.setField(onrampService, "banxaBaseUrl", "https://checkout.banxa.com");
        ReflectionTestUtils.setField(onrampService, "bipaBaseUrl", "https://bipa.app/buy/btc");
    }

    @Test
    public void testGenerateOnrampUrls() {
        Long userId = 1L;
        String mockAddress = "bc1qtestaddress";
        UserDataBase user = mock(UserDataBase.class);
        when(user.getId()).thenReturn(userId);

        WalletEntity mockWallet = new WalletEntity();
        mockWallet.setId(10L);
        mockWallet.setName("MAIN");
        mockWallet.setUser(user);
        mockWallet.setDepositAddress(mockAddress);
        mockWallet.setExternalWalletReference("STATIC_DERIVATION");

        when(walletService.findByUserId(userId)).thenReturn(Collections.singletonList(mockWallet));
        when(valueOperations.get(anyString())).thenReturn(null);
        when(externalTransfersPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(custodialAddressAllocator.allocate(eq(userId), any(WalletEntity.class), eq("onramp:MAIN"), eq(true)))
                .thenReturn(new CustodialAddressAllocator.Allocation(mockAddress, "XPUB_INDEX_0", "KEROSENE_LOCAL", false));

        Map<String, String> urls = onrampService.generateOnrampUrls(userId);

        assertNotNull(urls);
        assertTrue(urls.containsKey("moonpay"));
        assertTrue(urls.containsKey("banxa"));
        assertTrue(urls.containsKey("bipa"));

        assertTrue(urls.get("moonpay").contains("currencyCode=btc&walletAddress=" + mockAddress));
        assertTrue(urls.get("banxa").contains("walletAddress=" + mockAddress));
        assertTrue(urls.get("bipa").contains("address=" + mockAddress));
        assertEquals(mockAddress, urls.get("depositAddress"));
        assertNotNull(urls.get("transferId"));
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
