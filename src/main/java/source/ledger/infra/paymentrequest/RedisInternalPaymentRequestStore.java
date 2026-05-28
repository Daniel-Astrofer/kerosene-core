package source.ledger.infra.paymentrequest;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import source.ledger.application.paymentrequest.InternalPaymentRequestStore;
import source.ledger.dto.InternalPaymentRequestDTO;

import java.util.concurrent.TimeUnit;

@Component
public class RedisInternalPaymentRequestStore implements InternalPaymentRequestStore {

    private static final String REDIS_PREFIX = "internal_payment_req:";

    private final RedisTemplate<String, InternalPaymentRequestDTO> redisTemplate;

    public RedisInternalPaymentRequestStore(RedisTemplate<String, InternalPaymentRequestDTO> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(InternalPaymentRequestDTO request, long ttl, TimeUnit unit) {
        redisTemplate.opsForValue().set(REDIS_PREFIX + request.getId(), request, ttl, unit);
    }

    @Override
    public InternalPaymentRequestDTO findById(String linkId) {
        return redisTemplate.opsForValue().get(REDIS_PREFIX + linkId);
    }
}
