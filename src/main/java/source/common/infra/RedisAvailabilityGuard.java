package source.common.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Small connectivity probe used by scheduled jobs to skip cleanly while Redis
 * is unavailable instead of generating repeated stack traces.
 */
@Component
public class RedisAvailabilityGuard {

    private static final Logger log = LoggerFactory.getLogger(RedisAvailabilityGuard.class);

    private final Object monitor = new Object();
    private final StringRedisTemplate redisTemplate;

    private volatile long lastCheckAtMs;
    private volatile boolean lastAvailable = true;
    private volatile String lastFailureMessage = "";

    @Value("${redis.availability.cache-ms:5000}")
    private long cacheWindowMs;

    public RedisAvailabilityGuard(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isAvailable() {
        long now = System.currentTimeMillis();
        long cacheMs = Math.max(0L, cacheWindowMs);
        if ((now - lastCheckAtMs) < cacheMs) {
            return lastAvailable;
        }

        synchronized (monitor) {
            now = System.currentTimeMillis();
            if ((now - lastCheckAtMs) < cacheMs) {
                return lastAvailable;
            }

            try {
                String response = redisTemplate.execute((RedisCallback<String>) RedisConnection::ping);
                boolean available = response != null && "PONG".equalsIgnoreCase(response);
                updateState(available, available ? "" : "Unexpected Redis ping response: " + response);
            } catch (Exception exception) {
                updateState(false, exception.getMessage());
            } finally {
                lastCheckAtMs = now;
            }

            return lastAvailable;
        }
    }

    public String describeLastFailure() {
        return lastFailureMessage == null || lastFailureMessage.isBlank()
                ? "Redis unavailable"
                : lastFailureMessage;
    }

    private void updateState(boolean available, String failureMessage) {
        boolean previous = this.lastAvailable;
        this.lastAvailable = available;
        this.lastFailureMessage = failureMessage;

        if (available) {
            if (!previous) {
                log.info("[RedisAvailabilityGuard] Redis connectivity restored.");
            }
            return;
        }

        if (previous) {
            log.warn("[RedisAvailabilityGuard] Redis unavailable: {}", describeLastFailure());
        } else {
            log.debug("[RedisAvailabilityGuard] Redis still unavailable: {}", describeLastFailure());
        }
    }
}
