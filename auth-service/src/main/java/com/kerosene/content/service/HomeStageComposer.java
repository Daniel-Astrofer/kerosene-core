package com.kerosene.content.service;

import org.springframework.stereotype.Service;
import com.kerosene.common.service.TickerService;
import com.kerosene.content.dto.HomeRestingHeaderDTO;
import com.kerosene.content.dto.HomeStageDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the Communication Stage theater piece from live market data (and later catalog).
 *
 * <p>Defaults (product):
 * <ul>
 *   <li>playPolicy ONCE
 *   <li>actions BELOW_STAGE + ALWAYS_VISIBLE (never hide for text)
 *   <li>bodyShift slide + EASE_OUT_CUBIC
 *   <li>VIDEO would use HIDE_FOR_VIDEO (not used for market lines)
 * </ul>
 */
@Service
public class HomeStageComposer {

    private final TickerService tickerService;

    public HomeStageComposer(TickerService tickerService) {
        this.tickerService = tickerService;
    }

    public HomeRestingHeaderDTO restingHeader() {
        return HomeRestingHeaderDTO.defaults();
    }

    public HomeStageDTO compose(String locale, String balanceView) {
        String lang = normalizeLocale(locale);
        if (tickerService.getChange24hPercent("usd") == null) {
            try {
                tickerService.updatePrices();
            } catch (Exception ignored) {
                // offline / no egress — fall through to price-only or idle
            }
        }

        BigDecimal change = tickerService.getChange24hPercent("usd");
        BigDecimal usd = safePrice("usd");

        if (change != null) {
            return marketChangeStage(lang, change, usd);
        }
        if (usd != null && usd.signum() > 0) {
            return marketPriceStage(lang, usd);
        }
        return HomeStageDTO.idle();
    }

    private HomeStageDTO marketChangeStage(String lang, BigDecimal change, BigDecimal usd) {
        BigDecimal abs = change.abs().setScale(1, RoundingMode.HALF_UP);
        String pct = formatPercent(lang, abs);
        boolean up = change.signum() >= 0;
        String title = up
                ? t(lang,
                        "Bitcoin subiu " + pct + "% nas últimas 24h",
                        "Bitcoin is up " + pct + "% in the last 24h",
                        "Bitcoin subió " + pct + "% en las últimas 24h")
                : t(lang,
                        "Bitcoin caiu " + pct + "% nas últimas 24h",
                        "Bitcoin is down " + pct + "% in the last 24h",
                        "Bitcoin bajó " + pct + "% en las últimas 24h");
        int duration = marqueeMs(title);
        String accent = up ? "positive" : "danger";
        // Two glows: main wash + secondary offset highlight.
        HomeStageDTO.Atmosphere atmo = new HomeStageDTO.Atmosphere(
                java.util.List.of(
                        new HomeStageDTO.Glow("main", accent, 0.5, 0.0, 1.7, 0.52, 0.48, 0.72, 0),
                        new HomeStageDTO.Glow("rim", accent, up ? 0.72 : 0.28, 0.08, 0.9, 0.35, 0.22, 0.65, 1)),
                true,
                520);
        return bannerStage("stage-btc-24h", "MARKET", title, duration, "transparent", atmo);
    }

    private HomeStageDTO marketPriceStage(String lang, BigDecimal usd) {
        String price = formatMoney(lang, usd, "USD");
        String title = t(lang,
                "BTC cotado a " + price + " neste momento",
                "BTC trading at " + price + " right now",
                "BTC cotizado a " + price + " en este momento");
        HomeStageDTO.Atmosphere atmo = new HomeStageDTO.Atmosphere(
                java.util.List.of(
                        new HomeStageDTO.Glow("main", "amber", 0.5, 0.0, 1.6, 0.5, 0.4, 0.7, 0),
                        new HomeStageDTO.Glow("side", "white", 0.18, 0.12, 0.7, 0.3, 0.14, 0.6, 1)),
                true,
                480);
        return bannerStage("stage-btc-usd", "MARKET", title, marqueeMs(title), "transparent", atmo);
    }

    private HomeStageDTO bannerStage(
            String id,
            String kind,
            String title,
            int durationMs,
            String bgToken,
            HomeStageDTO.Atmosphere atmosphere) {
        return new HomeStageDTO(
                id,
                kind,
                "ONCE",
                100,
                new HomeStageDTO.Content(
                        title,
                        null,
                        "MARQUEE",
                        Map.of("name", false),
                        null),
                new HomeStageDTO.Media("NONE", null, null, null, 1.0, false, true, false),
                new HomeStageDTO.Layout(
                        "BANNER_TEXT",
                        bgToken,
                        new HomeStageDTO.Padding(0, 8, 0),
                        8,
                        0,
                        120,
                        new HomeStageDTO.ActionsLayout("BELOW_STAGE", "ALWAYS_VISIBLE"),
                        atmosphere),
                HomeStageDTO.defaultMotion(durationMs, 36),
                new HomeStageDTO.Lifecycle(durationMs, true, false),
                atmosphere);
    }

    private BigDecimal safePrice(String currency) {
        try {
            return tickerService.getPrice(currency);
        } catch (Exception e) {
            return null;
        }
    }

    static int marqueeMs(String text) {
        int len = text == null ? 0 : text.trim().length();
        return Math.min(12_000, Math.max(5_500, len * 90));
    }

    private static String normalizeLocale(String raw) {
        if (raw == null || raw.isBlank()) return "pt";
        String lang = raw.trim().toLowerCase(Locale.ROOT);
        if (lang.startsWith("en")) return "en";
        if (lang.startsWith("es")) return "es";
        return "pt";
    }

    private static String t(String lang, String pt, String en, String es) {
        return switch (lang) {
            case "en" -> en;
            case "es" -> es;
            default -> pt;
        };
    }

    private static String formatPercent(String lang, BigDecimal value) {
        Locale locale = switch (lang) {
            case "en" -> Locale.US;
            case "es" -> Locale.forLanguageTag("es-ES");
            default -> Locale.forLanguageTag("pt-BR");
        };
        NumberFormat nf = NumberFormat.getNumberInstance(locale);
        nf.setMinimumFractionDigits(1);
        nf.setMaximumFractionDigits(1);
        return nf.format(value);
    }

    private static String formatMoney(String lang, BigDecimal value, String currency) {
        Locale locale = switch (lang) {
            case "en" -> Locale.US;
            case "es" -> Locale.forLanguageTag("es-ES");
            default -> Locale.forLanguageTag("pt-BR");
        };
        NumberFormat nf = NumberFormat.getCurrencyInstance(locale);
        try {
            nf.setCurrency(java.util.Currency.getInstance(currency));
        } catch (Exception ignored) {
        }
        nf.setMaximumFractionDigits(0);
        nf.setMinimumFractionDigits(0);
        return nf.format(value);
    }
}
