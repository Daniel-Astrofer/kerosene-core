package source.common.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import source.auth.application.infra.persistence.jpa.UserRepository;
import source.auth.model.entity.UserDataBase;
import source.notification.service.NotificationService;
import source.common.financial.FinancialTickerPort;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Service to fetch real-time Bitcoin prices from CoinGecko.
 * Stores values in Redis for high-performance access by controllers.
 */
@Service
public class TickerService implements FinancialTickerPort {

    private static final Logger log = LoggerFactory.getLogger(TickerService.class);
    private static final String COINGECKO_URL =
            "https://api.coingecko.com/api/v3/simple/price"
                    + "?ids=bitcoin&vs_currencies=usd,brl,eur&include_24hr_change=true";
    private static final String REDIS_PRICE_KEY_PREFIX = "btc_price:";
    private static final String REDIS_CHANGE_24H_KEY_PREFIX = "btc_change_24h:";

    // Fallback prices in case the API is unreachable
    private static final BigDecimal FALLBACK_USD = new BigDecimal("65000");
    private static final BigDecimal FALLBACK_BRL = new BigDecimal("325000");
    private static final BigDecimal FALLBACK_EUR = new BigDecimal("60000");

    @Value("${ticker.coingecko.enabled:true}")
    private boolean coingeckoEnabled;

    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private long lastEngagementSentTime = 0;

    public TickerService(
            StringRedisTemplate redisTemplate,
            @Qualifier("tickerRestTemplate") RestTemplate restTemplate) {
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
        this.notificationService = null;
        this.userRepository = null;
    }

    @Autowired
    public TickerService(
            StringRedisTemplate redisTemplate,
            @Qualifier("tickerRestTemplate") RestTemplate restTemplate,
            NotificationService notificationService,
            UserRepository userRepository) {
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
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
        if (coingeckoEnabled) {
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
                    BigDecimal eur = toBigDecimal(prices.get("eur"));
                    BigDecimal usdChange = toBigDecimal(prices.get("usd_24h_change"));
                    BigDecimal brlChange = toBigDecimal(prices.get("brl_24h_change"));
                    BigDecimal eurChange = toBigDecimal(prices.get("eur_24h_change"));

                    if (usd != null) savePrice("usd", usd);
                    if (brl != null) savePrice("brl", brl);
                    if (eur != null) savePrice("eur", eur);
                    if (usdChange != null) saveChange24h("usd", usdChange);
                    if (brlChange != null) saveChange24h("brl", brlChange);
                    if (eurChange != null) saveChange24h("eur", eurChange);

                    log.info(
                            "[Ticker] Prices updated: USD={} ({}%), BRL={} ({}%), EUR={} ({}%)",
                            usd, usdChange, brl, brlChange, eur, eurChange);
                } else {
                    log.warn("[Ticker] CoinGecko returned no bitcoin node. Keeping cached/fallback prices.");
                }
            } catch (Exception e) {
                seedFallbackCacheIfReachable();
                log.warn("[Ticker] CoinGecko unavailable. Keeping cached/fallback prices: {}", e.getMessage());
            }
        } else {
            seedFallbackCacheIfReachable();
        }

        // Price fallback/cache is only for conversion display. Market movement
        // notifications must not be emitted from cached or fallback values.
    }

    private void seedFallbackCacheIfReachable() {
        try {
            ensurePricePresent("usd", FALLBACK_USD);
            ensurePricePresent("brl", FALLBACK_BRL);
            ensurePricePresent("eur", FALLBACK_EUR);
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

    private void saveChange24h(String currency, BigDecimal percent) {
        ValueOperations<String, String> valueOperations = redisTemplate.opsForValue();
        valueOperations.set(
                REDIS_CHANGE_24H_KEY_PREFIX + currency.toLowerCase(),
                percent.toPlainString(),
                15,
                TimeUnit.MINUTES);
    }

    /**
     * 24h percent change for BTC vs fiat (e.g. +2.45 or -1.10). Null if unknown.
     */
    public BigDecimal getChange24hPercent(String currency) {
        try {
            ValueOperations<String, String> valueOperations = redisTemplate.opsForValue();
            String val = valueOperations.get(REDIS_CHANGE_24H_KEY_PREFIX + currency.toLowerCase());
            if (val != null && !val.isBlank()) {
                return new BigDecimal(val);
            }
        } catch (Exception e) {
            log.warn("[Ticker] Redis 24h change read failed for {}: {}", currency, e.getMessage());
        }
        return null;
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

    @Override
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

        log.warn("[Ticker] Price not found in Redis for {}, using fallback", currency);
        if ("usd".equalsIgnoreCase(currency)) {
            return FALLBACK_USD;
        }
        if ("eur".equalsIgnoreCase(currency)) {
            return FALLBACK_EUR;
        }
        return FALLBACK_BRL;
    }

    public BigDecimal convertToFiat(BigDecimal btcAmount, String currency) {
        BigDecimal price = getPrice(currency);
        return btcAmount.multiply(price).setScale(2, RoundingMode.HALF_UP);
    }

    public Map<String, BigDecimal> getAllFiatValues(BigDecimal btcAmount) {
        return Map.of(
            "usd", convertToFiat(btcAmount, "usd"),
            "brl", convertToFiat(btcAmount, "brl"),
            "eur", convertToFiat(btcAmount, "eur")
        );
    }
}
