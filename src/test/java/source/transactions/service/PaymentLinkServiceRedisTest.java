package source.transactions.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import source.ledger.service.LedgerService;
import source.transactions.dto.PaymentLinkDTO;
import source.transactions.infra.BlockchainInfoClient;
import source.wallet.service.WalletService;
import source.wallet.model.WalletEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

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
    private BlockchainInfoClient blockchainInfoClient;

    @BeforeEach
    public void setup() {
        try {
            redisTemplate.getConnectionFactory().getConnection().flushDb();
        } catch (Exception e) {
            // ignore
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

        PaymentLinkDTO createdLink = paymentLinkService.createPaymentLink(userId, amountBtc, description);

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

        PaymentLinkDTO createdLink = paymentLinkService.createPaymentLink(userId, amountBtc, description);
        String linkId = createdLink.getId();

        // Recuperar do Redis
        PaymentLinkDTO retrievedLink = paymentLinkService.getPaymentLink(linkId);

        assertNotNull(retrievedLink);
        assertEquals(createdLink.getId(), retrievedLink.getId());
        assertEquals(amountBtc, retrievedLink.getAmountBtc());

        System.out.println("✅ Payment link recuperado com sucesso do Redis: " + linkId);
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

        // Mock Blockchain validation
        when(blockchainInfoClient.validateDepositTransaction(anyString(), anyString(), any(BigDecimal.class)))
                .thenReturn(true);

        PaymentLinkDTO link = paymentLinkService.createPaymentLink(userId, amount, description);
        String txid = "tx_mock_123";
        String fromAddress = "addr_from";

        PaymentLinkDTO confirmed = paymentLinkService.confirmPayment(link.getId(), txid, fromAddress);

        assertEquals("paid", confirmed.getStatus());
        assertEquals(txid, confirmed.getTxid());

        // VERIFY LEDGER CALL
        verify(ledgerService, times(1)).updateBalance(eq(999L), eq(amount), contains("PAYMENT_LINK_"));

        System.out.println("✅ Ledger creditado corretamente!");
    }

    /**
     * Testa se o Redis TTL funciona (3 horas)
     */
    @Test
    public void testRedisKeyTTL() {
        Long userId = 1L;
        BigDecimal amountBtc = new BigDecimal("0.5");
        String description = "Teste TTL";

        PaymentLinkDTO createdLink = paymentLinkService.createPaymentLink(userId, amountBtc, description);
        String linkId = createdLink.getId();
        String redisKey = "payment_link:" + linkId;

        // Verificar TTL (deve estar próximo a 3 horas = 10800 segundos)
        Long ttl = redisTemplate.getExpire(redisKey);

        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 10800, "TTL deve estar entre 0 e 10800 segundos");

        System.out.println("✅ Redis TTL válido: " + ttl + " segundos");
    }

    /**
     * Testa remoção manual do Redis
     */
    @Test
    public void testRemoveFromRedis() {
        Long userId = 1L;
        BigDecimal amountBtc = new BigDecimal("0.5");
        String description = "Teste remoção";

        PaymentLinkDTO createdLink = paymentLinkService.createPaymentLink(userId, amountBtc, description);
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

        System.out.println("✅ Payment link removido do Redis com sucesso");
    }
}
