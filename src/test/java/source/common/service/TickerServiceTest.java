package source.common.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TickerServiceTest {

    @Test
    void startupDoesNotTouchRedis() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        TickerService tickerService = new TickerService(redisTemplate, new RestTemplate());

        tickerService.initializeFallbackCache();

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void coinGeckoFailureDoesNotCrashWhenRedisIsUnavailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        TickerService tickerService = new TickerService(redisTemplate, restTemplate);
        ReflectionTestUtils.setField(tickerService, "coingeckoEnabled", true);

        when(restTemplate.getForObject(
                "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd,brl",
                Map.class))
                .thenThrow(new IllegalStateException("network unavailable"));
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));

        assertDoesNotThrow(tickerService::updatePrices);
    }
}
