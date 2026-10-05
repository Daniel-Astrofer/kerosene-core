package com.kerosene.content.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.kerosene.platform.market.TickerService;
import com.kerosene.content.dto.HomeActionVisibilityDTO;
import com.kerosene.content.dto.HomeGreetingDTO;
import com.kerosene.content.dto.HomeGreetingFallbackDTO;
import com.kerosene.content.dto.HomeGreetingMessageDTO;
import com.kerosene.content.dto.HomeGreetingPresentationDTO;
import com.kerosene.content.dto.HomeGreetingRotationDTO;
import com.kerosene.content.dto.HomeHeaderActionsDTO;
import com.kerosene.content.dto.HomeHeaderDTO;
import com.kerosene.content.dto.HomeHeaderSpacingDTO;
import com.kerosene.content.dto.HomeLayoutDTO;
import com.kerosene.content.dto.HomeStyleTokensDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Automatic home greeting insights from live market data.
 *
 * <p>Default play policy is ONCE: each market line scrolls once, header actions
 * hide while playing, then the UI restores (time-of-day + buttons). Balance can
 * be pushed down while the line is active — all flags are server-configurable.
 */
@Service
public class HomeMarketInsightService {

    /** Logger for recoverable market-data and warm-up failures. */
    private static final Logger log = LoggerFactory.getLogger(HomeMarketInsightService.class);

    /** Text length at which the header uses the longer marquee dwell interval. */
    static final int LONG_MESSAGE_CHARS = 28;

    /** Defaults — overridable later via home_ui_override presentation patch. */
    static final String DEFAULT_PLAY_POLICY = "ONCE";

    /** Hides header actions only while an ephemeral greeting is playing. */
    static final boolean DEFAULT_HIDE_ACTIONS_WHILE_PLAYING = true;

    /** Restores resting actions when ephemeral playback completes. */
    static final boolean DEFAULT_RESTORE_ACTIONS_AFTER = true;

    /** Moves the balance down to make room during greeting playback. */
    static final boolean DEFAULT_PUSH_DOWN_BALANCE = true;

    /** Vertical balance displacement in pixels while playback is active. */
    static final int DEFAULT_PUSH_DOWN_BALANCE_PX = 28;

    /** Requests a compact layout while an ephemeral greeting is playing. */
    static final boolean DEFAULT_COMPRESS_LAYOUT = true;

    /** Market ticker supplying current price and 24-hour movement values. */
    private final TickerService tickerService;

    /** Creates the insight composer with its market-data provider.
     * @param tickerService provider for prices and 24-hour changes
     */
    public HomeMarketInsightService(TickerService tickerService) {
        this.tickerService = tickerService;
    }

    /**
     * Builds the resting header and its optional localized market-message rotation.
     *
     * @param locale requested language or locale tag
     * @param balanceView balance presentation mode carried by the calling surface
     * @return complete header with resting actions and playback policy
     */
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

    /**
     * Returns the resting layout dimensions; playback-specific compression is
     * communicated separately through the greeting presentation settings.
     *
     * @param header composed header, retained for the layout composition contract
     * @return resting layout for the home surface
     */
    public HomeLayoutDTO composeLayout(HomeHeaderDTO header) {
        // Resting layout; client applies compress while ephemeral is playing.
        return new HomeLayoutDTO(18, 18, 24, null);
    }

    /**
     * Assembles at most two market messages, preferring 24-hour movement and USD price.
     * The ticker is warmed once when change data is absent; BRL is a Portuguese-only
     * fallback so the ONCE queue remains short.
     *
     * @param locale requested locale used for language selection
     * @param balanceView balance presentation mode (not used to expose balances here)
     * @return immutable ordered market-message list
     */
    List<HomeGreetingMessageDTO> buildMessages(String locale, String balanceView) {
        String lang = HomeLocaleSupport.normalizeLocale(locale);
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

    /** Formats a signed 24-hour movement insight and assigns its visual direction.
     * @param lang normalized language code @param change signed percentage change @return marquee message
     */
    private HomeGreetingMessageDTO changeMessage(String lang, BigDecimal change) {
        BigDecimal abs = change.abs().setScale(1, RoundingMode.HALF_UP);
        String pct = HomeLocaleSupport.formatPercent(lang, abs);
        boolean up = change.signum() >= 0;
        String text;
        if (up) {
            text = HomeLocaleSupport.translate(lang,
                    "Bitcoin subiu " + pct + "% nas últimas 24h",
                    "Bitcoin is up " + pct + "% in the last 24h",
                    "Bitcoin subió " + pct + "% en las últimas 24h");
        } else {
            text = HomeLocaleSupport.translate(lang,
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

    /** Formats the current USD-denominated BTC price for the selected language.
     * @param lang normalized language code @param usd positive USD price @return marquee message
     */
    private HomeGreetingMessageDTO priceUsdMessage(String lang, BigDecimal usd) {
        String price = HomeLocaleSupport.formatMoney(lang, usd, "USD");
        String text = HomeLocaleSupport.translate(lang,
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

    /** Formats a Portuguese BRL price message used only when the primary queue has room.
     * @param lang normalized language code @param brl positive BRL price @return marquee message
     */
    private HomeGreetingMessageDTO priceBrlMessage(String lang, BigDecimal brl) {
        String price = HomeLocaleSupport.formatMoney(lang, brl, "BRL");
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

    /** Rough dwell so a full marquee pass can finish.
     * Estimates the dwell time needed for one marquee pass, bounded to a readable range.
     */
    /** @param text marquee content @return playback duration in milliseconds */
    static int marqueeDurationMs(String text) {
        int len = text == null ? 0 : text.trim().length();
        // ~90ms per char, clamped 5.5s–12s
        return Math.min(12_000, Math.max(5_500, len * 90));
    }

    /** Reads a ticker price without allowing provider outages to break home composition.
     * @param currency ticker currency code @return available price, or {@code null} on failure
     */
    private BigDecimal safePrice(String currency) {
        try {
            return tickerService.getPrice(currency);
        } catch (Exception ex) {
            log.warn("Market insight price unavailable for {}: {}", currency, ex.getMessage());
            return null;
        }
    }

    /** Reports whether text meets the threshold for the long-form rotation interval.
     * @param text candidate greeting text @return {@code true} when the trimmed text is long
     */
    static boolean isLong(String text) {
        if (text == null) {
            return false;
        }
        return text.trim().length() >= LONG_MESSAGE_CHARS;
    }

}
