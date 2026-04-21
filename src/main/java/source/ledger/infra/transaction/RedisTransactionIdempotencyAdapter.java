package source.ledger.infra.transaction;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import source.ledger.application.transaction.TransactionIdempotencyPort;

import java.util.concurrent.TimeUnit;

@Component
public class RedisTransactionIdempotencyAdapter implements TransactionIdempotencyPort {

    private final StringRedisTemplate redisTemplate;

    public RedisTransactionIdempotencyAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean reserve(String key, long ttl, TimeUnit unit) {
        Boolean reserved = redisTemplate.opsForValue().setIfAbsent(key, "processing", ttl, unit);
        return Boolean.TRUE.equals(reserved);
    }
}
