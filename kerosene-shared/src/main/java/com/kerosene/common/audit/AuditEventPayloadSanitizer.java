package source.common.audit;

import source.common.infra.logging.LogSanitizer;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Converts audit payloads into bounded, log-safe metadata before hashing or
 * structured logging.
 */
public final class AuditEventPayloadSanitizer {

    private static final int MAX_KEYS = 32;
    private static final int MAX_VALUE_LENGTH = 256;
    private static final String MASKED = "[MASKED]";

    private AuditEventPayloadSanitizer() {
    }

    public static Map<String, Object> sanitize(Map<String, ?> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : payload.entrySet()) {
            if (entry.getKey() == null || sanitized.size() >= MAX_KEYS) {
                continue;
            }
            String key = safeKey(entry.getKey());
            sanitized.put(key, sanitizeValue(key, entry.getValue()));
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(sanitized));
    }

    private static Object sanitizeValue(String key, Object value) {
        if (value == null) {
            return null;
        }
        if (isSensitiveKey(key)) {
            return MASKED;
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof UUID || value instanceof Enum<?>) {
            return value;
        }
        if (value instanceof Collection<?> collection) {
            return "collection(size=" + collection.size() + ")";
        }
        if (value instanceof Map<?, ?> map) {
            return "map(size=" + map.size() + ")";
        }
        return limit(LogSanitizer.sanitizeFinancialPayload(String.valueOf(value)));
    }

    private static String safeKey(String key) {
        String sanitized = key.replaceAll("[^A-Za-z0-9_.-]", "_");
        if (sanitized.length() > 64) {
            return sanitized.substring(0, 64);
        }
        return sanitized;
    }

    private static boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(".", "");
        return LogSanitizer.isSensitiveKey(normalized)
                || normalized.contains("payload")
                || normalized.contains("body")
                || normalized.contains("credential")
                || normalized.contains("privatekey")
                || normalized.contains("macaroon")
                || normalized.contains("invoice");
    }

    private static String limit(String value) {
        if (value == null || value.length() <= MAX_VALUE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_VALUE_LENGTH);
    }
}
