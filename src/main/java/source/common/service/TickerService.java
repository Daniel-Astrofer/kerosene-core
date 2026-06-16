package source.common.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Service to fetch real-time Bitcoin prices from CoinGecko.
 * Stores values in Redis for high-performance access by controllers.
 */
@Service
public class TickerService {

    private static final Logger log = LoggerFactory.getLogger(TickerService.class);
    private static final String COINGECKO_URL = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd,brl";
    private static final String REDIS_PRICE_KEY_PREFIX = "btc_price:";

    // Fallback prices in case the API is unreachable
    private static final BigDecimal FALLBACK_USD = new BigDecimal("65000");
    private static final BigDecimal FALLBACK_BRL = new BigDecimal("325000");

    @Value("${ticker.coingecko.enabled:true}")
    private boolean coingeckoEnabled;

    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;

    public TickerService(
            StringRedisTemplate redisTemplate,
            @Qualifier("tickerRestTemplate") RestTemplate restTemplate) {
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    void initializeFallbackCache() {
        if (!coingeckoEnabled) {
            log.info("[Ticker] CoinGecko polling disabled for this profile. Using cached/fallback prices.");
        }
        log.info("[Ticker] Startup does not require Redis price cache warmup. In-memory fallback prices are available.");
    }

    /**
     * Poll CoinGecko every 5 minutes.
     */
    @Scheduled(fixedRate = 300000)
    public void updatePrices() {
        if (!coingeckoEnabled) {
            return;
        }

        try {
            log.info("[Ticker] Fetching BTC prices from CoinGecko...");
            Map<String, ?> response = restTemplate.getForObject(COINGECKO_URL, Map.class);

            if (response != null && response.containsKey("bitcoin")) {
                Object bitcoinNode = response.get("bitcoin");
                if (!(bitcoinNode instanceof Map<?, ?> prices)) {
                    log.warn("[Ticker] Unexpected payload structure from CoinGecko");
                    return;
                }

                BigDecimal usd = toBigDecimal(prices.get("usd"));
                BigDecimal brl = toBigDecimal(prices.get("brl"));

                if (usd != null) savePrice("usd", usd);
                if (brl != null) savePrice("brl", brl);

                log.info("[Ticker] Prices updated: USD={}, BRL={}", usd, brl);
                return;
            }

            log.warn("[Ticker] CoinGecko returned no bitcoin node. Keeping cached/fallback prices.");
        } catch (Exception e) {
            seedFallbackCacheIfReachable();
            log.warn("[Ticker] CoinGecko unavailable. Keeping cached/fallback prices: {}", e.getMessage());
        }
    }

    private void seedFallbackCacheIfReachable() {
        try {
            ensurePricePresent("usd", FALLBACK_USD);
            ensurePricePresent("brl", FALLBACK_BRL);
        } catch (Exception e) {
            log.warn("[Ticker] Redis fallback cache refresh unavailable. Using in-memory defaults: {}", e.getMessage());
        }
    }

    private void savePrice(String currency, BigDecimal value) {
        ValueOperations<String, String> valueOperations = redisTemplate.opsForValue();
        valueOperations.set(
                REDIS_PRICE_KEY_PREFIX + currency,
                value.toPlainString(),
                15,
                TimeUnit.MINUTES);
    }

    private void ensurePricePresent(String currency, BigDecimal fallbackValue) {
        String key = REDIS_PRICE_KEY_PREFIX + currency;
        ValueOperations<String, String> valueOperations = redisTemplate.opsForValue();
        String existing = valueOperations.get(key);
        if (existing == null || existing.isBlank()) {
            savePrice(currency, fallbackValue);
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return new BigDecimal(stringValue);
        }
        return null;
    }

    public BigDecimal getPrice(String currency) {
        try {
            ValueOperations<String, String> valueOperations = redisTemplate.opsForValue();
            String val = valueOperations.get(REDIS_PRICE_KEY_PREFIX + currency.toLowerCase());
            if (val != null) {
                return new BigDecimal(val);
            }
        } catch (Exception e) {
            log.warn("[Ticker] Redis read failed for {}: {}", currency, e.getMessage());
        }

        if ("usd".equalsIgnoreCase(currency)) {
            log.warn("[Ticker] Price not found in Redis for {}, using fallback", currency);
            return FALLBACK_USD;
        }
        log.warn("[Ticker] Price not found in Redis for {}, using fallback", currency);
        return FALLBACK_BRL;
    }

    public BigDecimal convertToFiat(BigDecimal btcAmount, String currency) {
        BigDecimal price = getPrice(currency);
        return btcAmount.multiply(price).setScale(2, RoundingMode.HALF_UP);
    }

    public Map<String, BigDecimal> getAllFiatValues(BigDecimal btcAmount) {
        return Map.of(
            "usd", convertToFiat(btcAmount, "usd"),
            "brl", convertToFiat(btcAmount, "brl")
        );
    }
}
