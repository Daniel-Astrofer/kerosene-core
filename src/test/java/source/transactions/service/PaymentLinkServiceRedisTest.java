package source.transactions.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import source.ledger.service.LedgerService;
import source.transactions.application.paymentlink.PaymentLinkAddressAllocationPort;
import source.transactions.application.paymentlink.PaymentLinkWalletPort;
import source.transactions.dto.PaymentLinkDTO;

import source.wallet.service.WalletService;
import source.wallet.service.WalletCardProfileService;
import source.wallet.model.WalletEntity;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
public class PaymentLinkServiceRedisTest {

    @Autowired
    private PaymentLinkService paymentLinkService;

    @Autowired
    private RedisTemplate<String, PaymentLinkDTO> redisTemplate;

    @MockBean
    private LedgerService ledgerService;

    @MockBean
    private WalletService walletService;

    @MockBean
    private PaymentLinkWalletPort paymentLinkWalletPort;

    @MockBean
    private PaymentLinkAddressAllocationPort paymentLinkAddressAllocationPort;

    @MockBean
    private WalletCardProfileService walletCardProfileService;

    @BeforeEach
    public void setup() {
        Assumptions.assumeTrue(isRedisAvailable(), "Requires a local Redis instance for payment link integration tests.");
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb(
                    org.springframework.data.redis.connection.RedisServerCommands.FlushOption.ASYNC);
        }
    }

    /**
     * Testa se um payment link é armazenado no Redis com expiração de 3 horas
     */
    @Test
    public void testPaymentLinkStoredInRedis() {
        Long userId = 1L;
        BigDecimal amountBtc = new BigDecimal("0.5");
        String description = "Depósito de teste";
        stubPrimaryWalletAllocation(userId, 501L, "bc1quserlink1");

        PaymentLinkDTO createdLink = paymentLinkService.createPaymentLink(userId, new source.transactions.dto.CreatePaymentLinkRequest(amountBtc, description, null, null, null, null, null, null));

        assertNotNull(createdLink);
        assertEquals("pending", createdLink.getStatus());

        // Validar que está no Redis
        String redisKey = "payment_link:" + createdLink.getId();
        PaymentLinkDTO dtoFromRedis = redisTemplate.opsForValue().get(redisKey);

        assertNotNull(dtoFromRedis);
        assertEquals(createdLink.getId(), dtoFromRedis.getId());
        assertEquals(amountBtc, dtoFromRedis.getAmountBtc());
        assertEquals(description, dtoFromRedis.getDescription());
    }

    /**
     * Testa se o payment link é recuperado do Redis
     */
    @Test
    public void testPaymentLinkRetrievedFromRedis() {
        Long userId = 1L;
        BigDecimal amountBtc = new BigDecimal("0.5");
        String description = "Teste Redis";
        stubPrimaryWalletAllocation(userId, 502L, "bc1quserlink2");

        PaymentLinkDTO createdLink = paymentLinkService.createPaymentLink(userId, new source.transactions.dto.CreatePaymentLinkRequest(amountBtc, description, null, null, null, null, null, null));
        String linkId = createdLink.getId();

        // Recuperar do Redis
        PaymentLinkDTO retrievedLink = paymentLinkService.getPaymentLink(linkId);

        assertNotNull(retrievedLink);
        assertEquals(createdLink.getId(), retrievedLink.getId());
        assertEquals(amountBtc, retrievedLink.getAmountBtc());
    }

    /**
     * Testa o fluxo de confirmação e creditamento na carteira
     */
    @Test
    public void testConfirmPaymentCreditsWallet() {
        Long userId = 123L;
        BigDecimal amount = new BigDecimal("1.0");
        String description = "Credit Test";

        // Mock Wallet
        WalletEntity mockWallet = new WalletEntity();
        mockWallet.setId(999L);
        when(walletService.findByUserId(userId)).thenReturn(Collections.singletonList(mockWallet));
        when(walletCardProfileService.calculateDepositFee(userId, amount)).thenReturn(new BigDecimal("0.00900000"));
        stubPrimaryWalletAllocation(userId, 999L, "bc1qcreditwallet");

        PaymentLinkDTO link = paymentLinkService.createPaymentLink(userId, new source.transactions.dto.CreatePaymentLinkRequest(amount, description, null, null, null, null, null, null));
        String txid = "tx_mock_123";
        String fromAddress = "addr_from";

        PaymentLinkDTO confirmed = paymentLinkService.confirmPayment(link.getId(), txid, fromAddress);

        assertEquals("paid", confirmed.getStatus());
        assertEquals(txid, confirmed.getTxid());
        assertEquals(new BigDecimal("1.0"), confirmed.getGrossAmountBtc());
        assertEquals(new BigDecimal("0.00900000"), confirmed.getDepositFeeBtc());
        assertEquals(new BigDecimal("0.99100000"), confirmed.getNetAmountBtc());
        verify(ledgerService, times(1)).updateBalance(eq(999L), eq(new BigDecimal("0.99100000")), contains("PAYMENT_LINK_"));
    }

    @Test
    public void testUserPaymentLinksReflectUpdatedPrimaryState() {
        Long userId = 777L;
        BigDecimal amount = new BigDecimal("0.25");

        WalletEntity mockWallet = new WalletEntity();
        mockWallet.setId(321L);
        when(walletService.findByUserId(userId)).thenReturn(Collections.singletonList(mockWallet));
        when(walletCardProfileService.calculateDepositFee(userId, amount)).thenReturn(BigDecimal.ZERO.setScale(8));
        stubPrimaryWalletAllocation(userId, 321L, "bc1qlistsync");

        PaymentLinkDTO link = paymentLinkService.createPaymentLink(userId, new source.transactions.dto.CreatePaymentLinkRequest(amount, "List sync test", null, null, null, null, null, null));
        paymentLinkService.confirmPayment(link.getId(), "tx_sync_1", "sender");

        List<PaymentLinkDTO> paymentLinks = paymentLinkService.getUserPaymentLinks(userId);

        assertEquals(1, paymentLinks.size());
        assertEquals(link.getId(), paymentLinks.get(0).getId());
        assertEquals("paid", paymentLinks.get(0).getStatus());
        assertEquals(new BigDecimal("0.25"), paymentLinks.get(0).getGrossAmountBtc());
        assertEquals(BigDecimal.ZERO.setScale(8), paymentLinks.get(0).getDepositFeeBtc());
        assertEquals(BigDecimal.ZERO.setScale(8).add(new BigDecimal("0.25")), paymentLinks.get(0).getNetAmountBtc());
    }

    @Test
    public void testOnboardingPaymentGoesToVerifyingStateWithoutCreditingWallet() {
        PaymentLinkDTO link = paymentLinkService.createOnboardingPaymentLink(
                "signup-session-1",
                new BigDecimal("0.00022000"),
                PaymentLinkService.ONBOARDING_VOUCHER_DESCRIPTION);

        PaymentLinkDTO confirmed = paymentLinkService.confirmPublicOnboardingPayment(
                link.getId(),
                "mock_tx_onboarding",
                "sender");

        assertEquals("verifying_onboarding", confirmed.getStatus());
        verify(ledgerService, never()).updateBalance(anyLong(), any(), anyString());
    }

    /**
     * Testa se o Redis TTL funciona (3 horas)
     */
    @Test
    public void testRedisKeyTTL() {
        Long userId = 1L;
        BigDecimal amountBtc = new BigDecimal("0.5");
        String description = "Teste TTL";
        stubPrimaryWalletAllocation(userId, 503L, "bc1qttlwallet");

        PaymentLinkDTO createdLink = paymentLinkService.createPaymentLink(userId, new source.transactions.dto.CreatePaymentLinkRequest(amountBtc, description, null, null, null, null, null, null));
        String linkId = createdLink.getId();
        String redisKey = "payment_link:" + linkId;

        // Verificar TTL (deve estar próximo a 3 horas = 10800 segundos)
        Long ttl = redisTemplate.getExpire(redisKey);

        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 10800, "TTL deve estar entre 0 e 10800 segundos");
    }

    /**
     * Testa remoção manual do Redis
     */
    @Test
    public void testRemoveFromRedis() {
        Long userId = 1L;
        BigDecimal amountBtc = new BigDecimal("0.5");
        String description = "Teste remoção";
        stubPrimaryWalletAllocation(userId, 504L, "bc1qremovewallet");

        PaymentLinkDTO createdLink = paymentLinkService.createPaymentLink(userId, new source.transactions.dto.CreatePaymentLinkRequest(amountBtc, description, null, null, null, null, null, null));
        String linkId = createdLink.getId();
        String redisKey = "payment_link:" + linkId;

        // Validar que está no Redis
        PaymentLinkDTO dtoFromRedis = redisTemplate.opsForValue().get(redisKey);
        assertNotNull(dtoFromRedis);

        // Remover do Redis
        paymentLinkService.removeFromRedis(linkId);

        // Validar que foi removido
        PaymentLinkDTO dtoAfterRemoval = redisTemplate.opsForValue().get(redisKey);
        assertNull(dtoAfterRemoval);
    }

    private void stubPrimaryWalletAllocation(Long userId, Long walletId, String depositAddress) {
        WalletEntity wallet = new WalletEntity();
        wallet.setId(walletId);
        wallet.setName("PRIMARY");

        when(paymentLinkWalletPort.findPrimaryWallet(userId)).thenReturn(wallet);
        when(paymentLinkAddressAllocationPort.allocate(eq(userId), eq(wallet), anyString(), eq(true)))
                .thenReturn(new PaymentLinkAddressAllocationPort.Allocation(
                        depositAddress,
                        "allocation-" + walletId,
                        "KEROSENE_LOCAL",
                        false));
    }

    private boolean isRedisAvailable() {
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping());
        } catch (Exception exception) {
            return false;
        }
    }
}
