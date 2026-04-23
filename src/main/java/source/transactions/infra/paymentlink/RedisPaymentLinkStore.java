package source.transactions.infra.paymentlink;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;
import source.transactions.application.paymentlink.PaymentLinkStore;
import source.transactions.dto.PaymentLinkDTO;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class RedisPaymentLinkStore implements PaymentLinkStore {

    private static final String REDIS_KEY_PREFIX = "payment_link:";
    private static final String REDIS_USER_INDEX_PREFIX = "user_payment_links:";
    private static final String REDIS_SESSION_INDEX_PREFIX = "session_payment_links:";
    private static final String REDIS_STATUS_INDEX_PREFIX = "status_payment_links:";
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
        PaymentLinkDTO existing = findById(paymentLink.getId()).orElse(null);

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

        if (paymentLink.getStatus() != null && !paymentLink.getStatus().isBlank()) {
            redisTemplate.opsForValue().set(statusIndexKey(paymentLink.getStatus(), paymentLink.getId()),
                    paymentLink,
                    ttlSeconds,
                    TimeUnit.SECONDS);
        }

        cleanupStaleIndexes(existing, paymentLink);
        return paymentLink;
    }

    @Override
    public Optional<PaymentLinkDTO> findById(String linkId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(primaryKey(linkId)));
    }

    @Override
    public List<PaymentLinkDTO> findByUserId(Long userId) {
        Set<String> keys = scanKeys(userIndexPattern(userId));
        if (keys.isEmpty()) {
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
        Set<String> keys = scanKeys(statusIndexPattern(status));
        if (keys.isEmpty()) {
            return List.of();
        }

        List<PaymentLinkDTO> paymentLinks = new ArrayList<>();
        for (String key : keys) {
            String linkId = extractLinkId(key);
            if (linkId == null) {
                continue;
            }

            Optional<PaymentLinkDTO> paymentLink = findById(linkId);
            if (paymentLink.isPresent() && status.equals(paymentLink.get().getStatus())) {
                paymentLinks.add(paymentLink.get());
            } else if (paymentLink.isEmpty()) {
                redisTemplate.delete(key);
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
            if (paymentLink.getStatus() != null && !paymentLink.getStatus().isBlank()) {
                redisTemplate.delete(statusIndexKey(paymentLink.getStatus(), paymentLink.getId()));
            }
        });
    }

    private void cleanupStaleIndexes(PaymentLinkDTO existing, PaymentLinkDTO updated) {
        if (existing == null) {
            return;
        }

        if (existing.getUserId() != null && !existing.getUserId().equals(updated.getUserId())) {
            redisTemplate.delete(userIndexKey(existing.getUserId(), existing.getId()));
        }
        if (existing.getSessionId() != null
                && !existing.getSessionId().isBlank()
                && !existing.getSessionId().equals(updated.getSessionId())) {
            redisTemplate.delete(sessionIndexKey(existing.getSessionId(), existing.getId()));
        }
        if (existing.getStatus() != null
                && !existing.getStatus().isBlank()
                && !existing.getStatus().equals(updated.getStatus())) {
            redisTemplate.delete(statusIndexKey(existing.getStatus(), existing.getId()));
        }
    }

    private Set<String> scanKeys(String pattern) {
        Set<String> keys = redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> collected = new HashSet<>();
            ScanOptions options = ScanOptions.scanOptions()
                    .match(pattern)
                    .count(256)
                    .build();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    String key = redisTemplate.getStringSerializer().deserialize(cursor.next());
                    if (key != null) {
                        collected.add(key);
                    }
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to scan Redis keys for pattern " + pattern, exception);
            }
            return collected;
        });
        return keys != null ? keys : Set.of();
    }

    private String primaryKey(String linkId) {
        return REDIS_KEY_PREFIX + linkId;
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

    private String statusIndexKey(String status, String linkId) {
        return REDIS_STATUS_INDEX_PREFIX + status + ":" + linkId;
    }

    private String statusIndexPattern(String status) {
        return REDIS_STATUS_INDEX_PREFIX + status + ":*";
    }

    private String extractLinkId(String key) {
        int lastSeparator = key.lastIndexOf(':');
        if (lastSeparator < 0 || lastSeparator == key.length() - 1) {
            return null;
        }
        return key.substring(lastSeparator + 1);
    }
}
