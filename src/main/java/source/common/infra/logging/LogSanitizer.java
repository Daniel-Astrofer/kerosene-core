package source.common.infra.logging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogSanitizer {

    private static final int MAX_PERSISTED_PAYLOAD_CHARS = 2048;

    private static final Pattern SENSITIVE_JSON_FIELD = Pattern.compile(
            "(?i)(\"(?:seed|mnemonic|private[_-]?key|passphrase|password|secret|token|authorization|cookie|macaroon|invoice|invoiceData|bolt11|paymentRequest)\"\\s*:\\s*\")([^\"]+)(\")");

    private static final Pattern SENSITIVE_KEY_VALUE = Pattern.compile(
            "(?i)\\b(seed|mnemonic|private[_-]?key|passphrase|password|secret|token|authorization|cookie|macaroon|invoice|invoiceData|bolt11|paymentRequest)\\b\\s*[:=]\\s*[^,}\\s]+");

    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]{16,}");

    private static final Pattern LIGHTNING_INVOICE = Pattern.compile(
            "(?i)\\bln(?:bc|tb|bcrt)[a-z0-9]{40,}\\b");

    private static final Pattern BECH32_ADDRESS = Pattern.compile(
            "(?i)\\b(?:bc1|tb1|bcrt1)[qpzry9x8gf2tvdw0s3jn54khce6mua7l]{25,90}\\b");

    private static final Pattern BASE58_ADDRESS = Pattern.compile(
            "\\b[123mn2][a-km-zA-HJ-NP-Z1-9]{25,90}\\b");

    private LogSanitizer() {
    }

    public static String fingerprint(String value) {
        if (value == null || value.isBlank()) {
            return "absent";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", exception);
        }
    }

    public static String fingerprint(byte[] value) {
        if (value == null || value.length == 0) {
            return "absent";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            return "sha256:" + HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", exception);
        }
    }

    public static String maskedIp(String ignored) {
        return "MASKED_IP";
    }

    public static String sanitizeFinancialPayload(String value) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            return "";
        }

        String sanitized = SENSITIVE_JSON_FIELD.matcher(value).replaceAll("$1***$3");
        sanitized = SENSITIVE_KEY_VALUE.matcher(sanitized).replaceAll(match -> {
            String text = match.group();
            int separator = firstSeparatorIndex(text);
            String key = separator >= 0 ? text.substring(0, separator + 1) : "";
            return Matcher.quoteReplacement(key + "***");
        });
        sanitized = BEARER_TOKEN.matcher(sanitized).replaceAll("Bearer ***");
        sanitized = maskMatches(sanitized, LIGHTNING_INVOICE);
        sanitized = maskMatches(sanitized, BECH32_ADDRESS);
        sanitized = maskMatches(sanitized, BASE58_ADDRESS);

        return sanitized.length() > MAX_PERSISTED_PAYLOAD_CHARS
                ? sanitized.substring(0, MAX_PERSISTED_PAYLOAD_CHARS)
                : sanitized;
    }

    private static String maskMatches(String value, Pattern pattern) {
        return pattern.matcher(value).replaceAll(match -> Matcher.quoteReplacement(maskToken(match.group())));
    }

    private static String maskToken(String token) {
        if (token == null || token.length() <= 12) {
            return "***";
        }
        return token.substring(0, Math.min(6, token.length()))
                + "..."
                + token.substring(token.length() - 4);
    }

    private static int firstSeparatorIndex(String value) {
        int colon = value.indexOf(':');
        int equals = value.indexOf('=');
        if (colon < 0) {
            return equals;
        }
        if (equals < 0) {
            return colon;
        }
        return Math.min(colon, equals);
    }
}
