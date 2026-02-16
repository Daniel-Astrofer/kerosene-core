package source.transactions.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import source.transactions.dto.PaymentLinkDTO;
import source.transactions.infra.BlockchainInfoClient;
import source.transactions.model.PaymentLinkEntity;
import source.transactions.repository.PaymentLinkRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
public class PaymentLinkServiceRedisTest {

    @Autowired
    private PaymentLinkService paymentLinkService;

    @Autowired
    private RedisTemplate<String, PaymentLinkDTO> redisTemplate;

    @Autowired
    private PaymentLinkRepository paymentLinkRepository;

    @BeforeEach
    public void setup() {
        // Limpar Redis antes de cada teste
        redisTemplate.getConnectionFactory().getConnection().flushDb();
    }

    /**
     * Testa se um payment link é armazenado no Redis com expiração de 3 horas
     */
    @Test
    public void testPaymentLinkStoredInRedis() {
        // Criar um payment link
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
     * Testa se o payment link é recuperado do Redis (mais rápido que do banco)
     */
    @Test
    public void testPaymentLinkRetrievedFromRedis() {
        // Criar um payment link
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
     * Testa se o status de expiração é sincronizado no Redis
     */
    @Test
    public void testPaymentLinkExpirationSync() {
        // Criar um payment link com expiração imediata
        Long userId = 1L;
        BigDecimal amountBtc = new BigDecimal("0.1");
        String description = "Link expirado";

        PaymentLinkDTO createdLink = paymentLinkService.createPaymentLink(userId, amountBtc, description);
        String linkId = createdLink.getId();

        // Simular expiração no banco
        PaymentLinkEntity entity = paymentLinkRepository.findById(linkId).orElseThrow();
        entity.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        paymentLinkRepository.save(entity);

        // Recuperar do Redis - deve validar expiração
        PaymentLinkDTO retrievedLink = paymentLinkService.getPaymentLink(linkId);

        assertEquals("expired", retrievedLink.getStatus());

        System.out.println("✅ Status de expiração sincronizado no Redis: " + linkId);
    }

    /**
     * Testa se o Redis TTL funciona (3 horas)
     */
    @Test
    public void testRedisKeyTTL() {
        // Criar um payment link
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

        System.out.println("✅ Redis TTL válido: " + ttl + " segundos (máximo 3 horas)");
    }

    /**
     * Testa remoção manual do Redis
     */
    @Test
    public void testRemoveFromRedis() {
        // Criar um payment link
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

        // Mas deve estar no banco
        PaymentLinkEntity entity = paymentLinkRepository.findById(linkId).orElse(null);
        assertNotNull(entity);

        System.out.println("✅ Payment link removido do Redis com sucesso");
    }
}
