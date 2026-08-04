package com.kerosene.auth.application.infra.persistence.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisRepositoryTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private RedisRepository repository;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        repository = new RedisRepository(new com.fasterxml.jackson.databind.ObjectMapper(), redis);
    }

    @Test
    void incrementWithExpireReturnsValueFromLuaScript() {
        when(redis.execute(any(DefaultRedisScript.class), eq(List.of("ratelimit:test:1")), eq("60")))
                .thenReturn(1L);

        Long result = repository.incrementWithExpire("ratelimit:test:1", 60);

        assertThat(result).isEqualTo(1L);
    }

    @Test
    void incrementWithExpireReturnsTwoOnSecondCall() {
        when(redis.execute(any(DefaultRedisScript.class), eq(List.of("ratelimit:test:2")), eq("60")))
                .thenReturn(2L);

        Long result = repository.incrementWithExpire("ratelimit:test:2", 60);

        assertThat(result).isEqualTo(2L);
    }

    @Test
    void incrementWithExpireReturnsNullOnRedisFailure() {
        when(redis.execute(any(DefaultRedisScript.class), any(List.class), any(String.class)))
                .thenThrow(new RuntimeException("Redis down"));

        Long result = repository.incrementWithExpire("ratelimit:test:3", 60);

        assertThat(result).isNull();
    }

    @Test
    void incrementDelegatesToRedis() {
        when(valueOps.increment("key1")).thenReturn(5L);

        Long result = repository.increment("key1");

        assertThat(result).isEqualTo(5L);
        verify(valueOps).increment("key1");
    }

    @Test
    void incrementReturnsOneWhenRedisReturnsNull() {
        when(valueOps.increment("key2")).thenReturn(null);

        Long result = repository.increment("key2");

        assertThat(result).isEqualTo(1L);
    }
}
