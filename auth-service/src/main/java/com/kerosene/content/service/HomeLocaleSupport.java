package com.kerosene.content.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/** Shared locale normalization and number formatting for generated home content. */
final class HomeLocaleSupport {

    /** Prevents instantiation of this stateless formatting utility. */
    private HomeLocaleSupport() {
    }

    /** Normalizes supported locale tags to the product's Portuguese, English, or Spanish set. */
    static String normalizeLocale(String raw) {
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

    /** Selects the translated string for a normalized language, defaulting to Portuguese. */
    static String translate(String lang, String pt, String en, String es) {
        return switch (lang) {
            case "en" -> en;
            case "es" -> es;
            default -> pt;
        };
    }

    /** Formats a percentage with one fractional digit using the language's separators. */
    static String formatPercent(String lang, BigDecimal value) {
        Locale locale = localeFor(lang);
        NumberFormat nf = NumberFormat.getNumberInstance(locale);
        nf.setMinimumFractionDigits(1);
        nf.setMaximumFractionDigits(1);
        return nf.format(value);
    }

    /** Formats a currency amount without fractional digits; invalid currency codes retain locale defaults. */
    static String formatMoney(String lang, BigDecimal value, String currency) {
        Locale locale = localeFor(lang);
        NumberFormat nf = NumberFormat.getCurrencyInstance(locale);
        try {
            nf.setCurrency(java.util.Currency.getInstance(currency));
        } catch (Exception ignored) {
        }
        nf.setMaximumFractionDigits(0);
        nf.setMinimumFractionDigits(0);
        return nf.format(value);
    }

    /** Maps a normalized language code to its regional formatting locale. */
    private static Locale localeFor(String lang) {
        return switch (lang) {
            case "en" -> Locale.US;
            case "es" -> Locale.forLanguageTag("es-ES");
            default -> Locale.forLanguageTag("pt-BR");
        };
    }
}
