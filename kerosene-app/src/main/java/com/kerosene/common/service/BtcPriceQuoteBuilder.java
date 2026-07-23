package com.kerosene.common.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared BTC quote map for HTTP {@code /api/economy/btc-price} and STOMP
 * {@code /topic/btc-price}.
 */
public final class BtcPriceQuoteBuilder {

    private BtcPriceQuoteBuilder() {}

    public static Map<String, Object> build(TickerService tickerService) {
        BigDecimal btcUsd = tickerService.getPrice("usd");
        BigDecimal btcBrl = tickerService.getPrice("brl");
        BigDecimal btcEur = tickerService.getPrice("eur");
        BigDecimal usdBrl = BigDecimal.ZERO;

        if (btcUsd != null && btcUsd.compareTo(BigDecimal.ZERO) > 0
                && btcBrl != null) {
            usdBrl = btcBrl.divide(btcUsd, 8, RoundingMode.HALF_UP);
        }

        BigDecimal change24h = tickerService.getChange24hPercent("usd");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("btcUsd", btcUsd);
        data.put("btcBrl", btcBrl);
        data.put("btcEur", btcEur);
        data.put("usdBrl", usdBrl);
        if (change24h != null) {
            data.put("btcUsdChange24hPercent", change24h);
        }
        data.put("updatedAt", Instant.now().toString());
        return data;
    }

    /**
     * Build from freshly fetched CoinGecko values (before Redis round-trip).
     */
    public static Map<String, Object> buildFromFetched(
            BigDecimal usd,
            BigDecimal brl,
            BigDecimal eur,
            BigDecimal usdChange24h) {
        BigDecimal usdBrl = BigDecimal.ZERO;
        if (usd != null && usd.compareTo(BigDecimal.ZERO) > 0 && brl != null) {
            usdBrl = brl.divide(usd, 8, RoundingMode.HALF_UP);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("btcUsd", usd);
        data.put("btcBrl", brl);
        data.put("btcEur", eur);
        data.put("usdBrl", usdBrl);
        if (usdChange24h != null) {
            data.put("btcUsdChange24hPercent", usdChange24h);
        }
        data.put("updatedAt", Instant.now().toString());
        return data;
    }
}
