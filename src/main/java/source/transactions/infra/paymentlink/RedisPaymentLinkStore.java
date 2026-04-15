package source.transactions.infra.paymentlink;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import source.transactions.application.paymentlink.PaymentLinkStore;
import source.transactions.dto.PaymentLinkDTO;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class RedisPaymentLinkStore implements PaymentLinkStore {

    private static final String REDIS_KEY_PREFIX = "payment_link:";
    private static final String REDIS_USER_INDEX_PREFIX = "user_payment_links:";
    private static final String REDIS_SESSION_INDEX_PREFIX = "session_payment_links:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(3);

    private final RedisTemplate<String, PaymentLinkDTO> redisTemplate;

    public RedisPaymentLinkStore(RedisTemplate<String, PaymentLinkDTO> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public PaymentLinkDTO save(PaymentLinkDTO paymentLink) {
        return save(paymentLink, DEFAULT_TTL);
    }

    @Override
    public PaymentLinkDTO save(PaymentLinkDTO paymentLink, Duration ttl) {
        long ttlSeconds = Math.max(1L, ttl.toSeconds());
        redisTemplate.opsForValue().set(primaryKey(paymentLink.getId()), paymentLink, ttlSeconds, TimeUnit.SECONDS);

        if (paymentLink.getUserId() != null) {
            redisTemplate.opsForValue().set(userIndexKey(paymentLink.getUserId(), paymentLink.getId()),
                    paymentLink,
                    ttlSeconds,
                    TimeUnit.SECONDS);
        }

        if (paymentLink.getSessionId() != null && !paymentLink.getSessionId().isBlank()) {
            redisTemplate.opsForValue().set(sessionIndexKey(paymentLink.getSessionId(), paymentLink.getId()),
                    paymentLink,
                    ttlSeconds,
                    TimeUnit.SECONDS);
        }

        return paymentLink;
    }

    @Override
    public Optional<PaymentLinkDTO> findById(String linkId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(primaryKey(linkId)));
    }

    @Override
    public List<PaymentLinkDTO> findByUserId(Long userId) {
        Set<String> keys = redisTemplate.keys(userIndexPattern(userId));
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }

        List<PaymentLinkDTO> paymentLinks = new ArrayList<>();
        for (String key : keys) {
            String linkId = extractLinkId(key);
            if (linkId == null) {
                continue;
            }

            Optional<PaymentLinkDTO> paymentLink = findById(linkId);
            if (paymentLink.isPresent()) {
                paymentLinks.add(paymentLink.get());
            } else {
                redisTemplate.delete(key);
            }
        }

        return paymentLinks;
    }

    @Override
    public List<PaymentLinkDTO> findByStatus(String status) {
        Set<String> keys = redisTemplate.keys(primaryPattern());
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }

        List<PaymentLinkDTO> paymentLinks = new ArrayList<>();
        for (String key : keys) {
            PaymentLinkDTO paymentLink = redisTemplate.opsForValue().get(key);
            if (paymentLink != null && status.equals(paymentLink.getStatus())) {
                paymentLinks.add(paymentLink);
            }
        }
        return paymentLinks;
    }

    @Override
    public void delete(String linkId) {
        Optional<PaymentLinkDTO> existing = findById(linkId);
        redisTemplate.delete(primaryKey(linkId));

        existing.ifPresent(paymentLink -> {
            if (paymentLink.getUserId() != null) {
                redisTemplate.delete(userIndexKey(paymentLink.getUserId(), paymentLink.getId()));
            }
            if (paymentLink.getSessionId() != null && !paymentLink.getSessionId().isBlank()) {
                redisTemplate.delete(sessionIndexKey(paymentLink.getSessionId(), paymentLink.getId()));
            }
        });
    }

    private String primaryKey(String linkId) {
        return REDIS_KEY_PREFIX + linkId;
    }

    private String primaryPattern() {
        return REDIS_KEY_PREFIX + "*";
    }

    private String userIndexKey(Long userId, String linkId) {
        return REDIS_USER_INDEX_PREFIX + userId + ":" + linkId;
    }

    private String userIndexPattern(Long userId) {
        return REDIS_USER_INDEX_PREFIX + userId + ":*";
    }

    private String sessionIndexKey(String sessionId, String linkId) {
        return REDIS_SESSION_INDEX_PREFIX + sessionId + ":" + linkId;
    }

    private String extractLinkId(String key) {
        int lastSeparator = key.lastIndexOf(':');
        if (lastSeparator < 0 || lastSeparator == key.length() - 1) {
            return null;
        }
        return key.substring(lastSeparator + 1);
    }
}
