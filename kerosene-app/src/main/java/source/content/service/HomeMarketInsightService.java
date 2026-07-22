package source.content.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import source.common.service.TickerService;
import source.content.dto.HomeActionVisibilityDTO;
import source.content.dto.HomeGreetingDTO;
import source.content.dto.HomeGreetingFallbackDTO;
import source.content.dto.HomeGreetingMessageDTO;
import source.content.dto.HomeGreetingPresentationDTO;
import source.content.dto.HomeGreetingRotationDTO;
import source.content.dto.HomeHeaderActionsDTO;
import source.content.dto.HomeHeaderDTO;
import source.content.dto.HomeHeaderSpacingDTO;
import source.content.dto.HomeLayoutDTO;
import source.content.dto.HomeStyleTokensDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Automatic home greeting insights from live market data.
 *
 * <p>Default play policy is ONCE: each market line scrolls once, header actions
 * hide while playing, then the UI restores (time-of-day + buttons). Balance can
 * be pushed down while the line is active — all flags are server-configurable.
 */
@Service
public class HomeMarketInsightService {

    private static final Logger log = LoggerFactory.getLogger(HomeMarketInsightService.class);

    static final int LONG_MESSAGE_CHARS = 28;

    /** Defaults — overridable later via home_ui_override presentation patch. */
    static final String DEFAULT_PLAY_POLICY = "ONCE";
    static final boolean DEFAULT_HIDE_ACTIONS_WHILE_PLAYING = true;
    static final boolean DEFAULT_RESTORE_ACTIONS_AFTER = true;
    static final boolean DEFAULT_PUSH_DOWN_BALANCE = true;
    static final int DEFAULT_PUSH_DOWN_BALANCE_PX = 28;
    static final boolean DEFAULT_COMPRESS_LAYOUT = true;

    private final TickerService tickerService;

    public HomeMarketInsightService(TickerService tickerService) {
        this.tickerService = tickerService;
    }

    public HomeHeaderDTO composeHeader(String locale, String balanceView) {
        List<HomeGreetingMessageDTO> messages = buildMessages(locale, balanceView);
        boolean hasMessages = !messages.isEmpty();
        boolean longForm = messages.stream().anyMatch(m -> isLong(m.text()));

        // Resting actions are always visible; client hides them only while playing.
        HomeHeaderActionsDTO restingActions = new HomeHeaderActionsDTO(
                new HomeActionVisibilityDTO(true),
                new HomeActionVisibilityDTO(true),
                new HomeActionVisibilityDTO(true));

        HomeGreetingPresentationDTO presentation = new HomeGreetingPresentationDTO(
                DEFAULT_PLAY_POLICY,
                DEFAULT_HIDE_ACTIONS_WHILE_PLAYING,
                DEFAULT_RESTORE_ACTIONS_AFTER,
                DEFAULT_PUSH_DOWN_BALANCE,
                DEFAULT_PUSH_DOWN_BALANCE_PX,
                DEFAULT_COMPRESS_LAYOUT);

        // Per-message dwell: longer for marquee so the full scroll is readable.
        int intervalMs = longForm ? 9000 : 5500;

        String mode = hasMessages ? "EPHEMERAL" : "STATIC";
        HomeGreetingDTO greeting = new HomeGreetingDTO(
                mode,
                new HomeGreetingFallbackDTO("TIME_OF_DAY", true),
                messages,
                new HomeGreetingRotationDTO(intervalMs, false),
                new HomeStyleTokensDTO("white", "w300"),
                presentation);

        // Spacing while resting (client can compress during play via presentation).
        HomeHeaderSpacingDTO spacing = new HomeHeaderSpacingDTO(8, 12);

        return new HomeHeaderDTO(greeting, restingActions, spacing);
    }

    public HomeLayoutDTO composeLayout(HomeHeaderDTO header) {
        // Resting layout; client applies compress while ephemeral is playing.
        return new HomeLayoutDTO(18, 18, 24, null);
    }

    List<HomeGreetingMessageDTO> buildMessages(String locale, String balanceView) {
        String lang = normalizeLocale(locale);
        List<HomeGreetingMessageDTO> out = new ArrayList<>();

        if (tickerService.getChange24hPercent("usd") == null) {
            try {
                tickerService.updatePrices();
            } catch (Exception ex) {
                log.debug("Market warm-up skipped: {}", ex.getMessage());
            }
        }

        BigDecimal change = tickerService.getChange24hPercent("usd");
        BigDecimal usd = safePrice("usd");
        BigDecimal brl = safePrice("brl");

        // Prefer the most important insight first (24h move).
        if (change != null) {
            out.add(changeMessage(lang, change));
        }
        if (usd != null && usd.signum() > 0) {
            out.add(priceUsdMessage(lang, usd));
        }
        // Only one primary market banner when ONCE — keep queue short (max 2).
        if (out.size() < 2 && brl != null && brl.signum() > 0 && "pt".equals(lang)) {
            out.add(priceBrlMessage(lang, brl));
        }

        return List.copyOf(out);
    }

    private HomeGreetingMessageDTO changeMessage(String lang, BigDecimal change) {
        BigDecimal abs = change.abs().setScale(1, RoundingMode.HALF_UP);
        String pct = formatPercent(lang, abs);
        boolean up = change.signum() >= 0;
        String text;
        if (up) {
            text = t(lang,
                    "Bitcoin subiu " + pct + "% nas últimas 24h",
                    "Bitcoin is up " + pct + "% in the last 24h",
                    "Bitcoin subió " + pct + "% en las últimas 24h");
        } else {
            text = t(lang,
                    "Bitcoin caiu " + pct + "% nas últimas 24h",
                    "Bitcoin is down " + pct + "% in the last 24h",
                    "Bitcoin bajó " + pct + "% en las últimas 24h");
        }
        int duration = marqueeDurationMs(text);
        return new HomeGreetingMessageDTO(
                "insight-btc-24h",
                text,
                duration,
                100,
                "MARQUEE",
                new HomeStyleTokensDTO(up ? "positive" : "danger", "w300"),
                null);
    }

    private HomeGreetingMessageDTO priceUsdMessage(String lang, BigDecimal usd) {
        String price = formatMoney(lang, usd, "USD");
        String text = t(lang,
                "BTC cotado a " + price + " neste momento",
                "BTC trading at " + price + " right now",
                "BTC cotizado a " + price + " en este momento");
        return new HomeGreetingMessageDTO(
                "insight-btc-usd",
                text,
                marqueeDurationMs(text),
                80,
                "MARQUEE",
                new HomeStyleTokensDTO("white", "w300"),
                null);
    }

    private HomeGreetingMessageDTO priceBrlMessage(String lang, BigDecimal brl) {
        String price = formatMoney(lang, brl, "BRL");
        String text = "Bitcoin a " + price + " neste momento";
        return new HomeGreetingMessageDTO(
                "insight-btc-brl",
                text,
                marqueeDurationMs(text),
                70,
                "MARQUEE",
                new HomeStyleTokensDTO("white", "w300"),
                null);
    }

    /** Rough dwell so a full marquee pass can finish. */
    static int marqueeDurationMs(String text) {
        int len = text == null ? 0 : text.trim().length();
        // ~90ms per char, clamped 5.5s–12s
        return Math.min(12_000, Math.max(5_500, len * 90));
    }

    private BigDecimal safePrice(String currency) {
        try {
            return tickerService.getPrice(currency);
        } catch (Exception ex) {
            log.warn("Market insight price unavailable for {}: {}", currency, ex.getMessage());
            return null;
        }
    }

    static boolean isLong(String text) {
        if (text == null) {
            return false;
        }
        return text.trim().length() >= LONG_MESSAGE_CHARS;
    }

    private static String normalizeLocale(String raw) {
        if (raw == null || raw.isBlank()) {
            return "pt";
        }
        String lang = raw.trim().toLowerCase(Locale.ROOT);
        if (lang.startsWith("en")) {
            return "en";
        }
        if (lang.startsWith("es")) {
            return "es";
        }
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
            // keep locale default
        }
        nf.setMaximumFractionDigits(0);
        nf.setMinimumFractionDigits(0);
        return nf.format(value);
    }
}
