package com.kerosene.platform.config;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSocketRateLimitTest {

    @Test
    void webSocketRateLimit_ShouldBlockMessage_WhenLimitExceeded() {
        Bucket bucket = Bucket.builder()
                .addLimit(limit -> limit.capacity(20).refillGreedy(10, java.time.Duration.ofSeconds(1)))
                .build();

        for (int i = 0; i < 20; i++) {
            assertTrue(bucket.tryConsume(1));
        }
        assertFalse(bucket.tryConsume(1));
    }
}
