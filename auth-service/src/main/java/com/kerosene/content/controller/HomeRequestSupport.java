package com.kerosene.content.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Shared extraction and normalization helpers for home-content request context. */
final class HomeRequestSupport {

    /** Prevents instantiation of this stateless request utility. */
    private HomeRequestSupport() {
    }

    /** Returns the first non-null, non-blank value after trimming it. */
    static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    /** Extracts the primary language subtag from an Accept-Language header. */
    static String languageFromAccept(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return null;
        }
        String primary = acceptLanguage.split(",")[0].trim();
        int dash = primary.indexOf('-');
        if (dash > 0) {
            primary = primary.substring(0, dash);
        }
        int semi = primary.indexOf(';');
        if (semi > 0) {
            primary = primary.substring(0, semi);
        }
        return primary.isBlank() ? null : primary.toLowerCase();
    }

    /** Reads the authenticated principal name as a numeric account identifier. */
    static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            return null;
        }
        try {
            return Long.parseLong(auth.getName());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
