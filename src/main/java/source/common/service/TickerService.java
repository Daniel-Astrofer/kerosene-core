package source.common.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;

    public TickerService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.restTemplate = new RestTemplate();
    }

    @PostConstruct
    void initializeFallbackCache() {
        ensurePricePresent("usd", FALLBACK_USD);
        ensurePricePresent("brl", FALLBACK_BRL);
    }

    /**
     * Poll CoinGecko every 5 minutes.
     */
    @Scheduled(fixedRate = 300000)
    public void updatePrices() {
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
            ensurePricePresent("usd", FALLBACK_USD);
            ensurePricePresent("brl", FALLBACK_BRL);
            log.warn("[Ticker] CoinGecko unavailable. Keeping cached/fallback prices: {}", e.getMessage());
        }
    }

    private void savePrice(String currency, BigDecimal value) {
        redisTemplate.opsForValue().set(
            REDIS_PRICE_KEY_PREFIX + currency,
            value.toPlainString(),
            15, TimeUnit.MINUTES
        );
    }

    private void ensurePricePresent(String currency, BigDecimal fallbackValue) {
        String key = REDIS_PRICE_KEY_PREFIX + currency;
        String existing = redisTemplate.opsForValue().get(key);
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
        String val = redisTemplate.opsForValue().get(REDIS_PRICE_KEY_PREFIX + currency.toLowerCase());
        if (val == null) {
            log.warn("[Ticker] Price not found in Redis for {}, using fallback", currency);
            return "usd".equalsIgnoreCase(currency) ? FALLBACK_USD : FALLBACK_BRL;
        }
        return new BigDecimal(val);
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
