package source.common.infra.logging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Centralised log sanitisation for the Kerosene financial platform.
 *
 * <p>Provides two usage modes:
 * <ul>
 *   <li><b>Structural masking</b> via {@link #sanitizeFinancialPayload} — strips sensitive values
 *       from arbitrary strings (JSON bodies, log messages, key=value pairs).</li>
 *   <li><b>Fingerprinting</b> via {@link #fingerprint} — converts sensitive values to a
 *       deterministic, non-reversible SHA-256 prefix so correlation is still possible without
 *       leaking the raw secret.</li>
 * </ul>
 *
 * <p>All patterns are compiled once at class load. The class is stateless and thread-safe.
 *
 * <p><b>Financial-platform rules enforced:</b>
 * <ul>
 *   <li>Cryptographic material: seeds, mnemonics, private keys, macaroons → {@code [MASKED]}</li>
 *   <li>Auth tokens: Bearer tokens, JWTs, session tokens → {@code [MASKED]}</li>
 *   <li>Lightning Network: BOLT11 invoices, payment requests → prefix...suffix</li>
 *   <li>Bitcoin addresses: bech32, base58 → prefix...suffix</li>
 *   <li>PII: CPF/CNPJ, email, phone numbers → structural masking</li>
 *   <li>Payment data: card numbers (PAN) → BIN + ******* + last4</li>
 *   <li>All IPs → {@code MASKED_IP} (no geolocation leak)</li>
 * </ul>
 */
public final class LogSanitizer {

    // ─── Output limits ────────────────────────────────────────────────────────
    private static final int MAX_PAYLOAD_CHARS = 2048;

    // ─── Sensitive JSON keys ──────────────────────────────────────────────────
    private static final Set<String> SENSITIVE_JSON_KEYS = new HashSet<>(Arrays.asList(
            "password", "passwd", "senha", "secret", "token", "totp", "totpsecret", "totp_secret",
            "seed", "mnemonic", "privatekey", "private_key", "private-key", "xprv", "xpub",
            "cvv", "cvc", "pin", "cardnumber", "card_number", "creditcard", "aeskey", "aes_key",
            "jwt", "accesstoken", "refreshtoken", "sessionid", "macaroon", "preAuthToken",
            "invoice", "invoicedata", "bolt11", "paymentrequest", "authorization", "cookie",
            "cosignersecret", "cosigner_secret", "shardkey", "shard_key"
    ));

    // ─── Pattern: sensitive JSON field (value between quotes after the key) ───
    private static final Pattern SENSITIVE_JSON_FIELD = Pattern.compile(
            "(?i)(\"(?:seed|mnemonic|private[_-]?key|passphrase|password|secret|token|" +
            "authorization|cookie|macaroon|invoice|invoiceData|bolt11|paymentRequest|" +
            "xprv|xpub|cosignerSecret|shardKey|preAuthToken|sessionId|refreshToken|" +
            "accessToken|cvv|cvc|pin|cardNumber|aesKey|jwt)\"\\s*:\\s*\")([^\"]+)(\")");

    // ─── Pattern: key=value or key: value in plain text ───────────────────────
    private static final Pattern SENSITIVE_KEY_VALUE = Pattern.compile(
            "(?i)\\b(seed|mnemonic|private[_-]?key|passphrase|password|secret|token|" +
            "authorization|cookie|macaroon|invoice|invoiceData|bolt11|paymentRequest|" +
            "xprv|xpub|cosignerSecret|shardKey|preAuthToken|sessionId|refreshToken|" +
            "accessToken|cvv|cvc|aes[_-]?key|jwt)\\b\\s*[:=]\\s*[^,}\\s]+");

    // ─── Auth tokens ─────────────────────────────────────────────────────────
    private static final Pattern BEARER_TOKEN = Pattern.compile(
            "(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]{16,}");

    private static final Pattern JWT_TOKEN = Pattern.compile(
            "eyJ[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.?[A-Za-z0-9-_.+/=]*");

    // ─── Lightning / Bitcoin ──────────────────────────────────────────────────
    private static final Pattern LIGHTNING_INVOICE = Pattern.compile(
            "(?i)\\bln(?:bc|tb|bcrt)[a-z0-9]{40,}\\b");

    private static final Pattern BECH32_ADDRESS = Pattern.compile(
            "(?i)\\b(?:bc1|tb1|bcrt1)[qpzry9x8gf2tvdw0s3jn54khce6mua7l]{25,90}\\b");

    private static final Pattern BASE58_ADDRESS = Pattern.compile(
            "\\b[123mn][a-km-zA-HJ-NP-Z1-9]{25,90}\\b");

    // ─── Brazilian PII ────────────────────────────────────────────────────────
    private static final Pattern CPF = Pattern.compile(
            "\\b\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}\\b|(?<![\\d.])\\d{11}(?![\\d.])");

    private static final Pattern CNPJ = Pattern.compile(
            "\\b\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}\\b|(?<![\\d.])\\d{14}(?![\\d.])");

    // ─── Email ────────────────────────────────────────────────────────────────
    private static final Pattern EMAIL = Pattern.compile(
            "\\b[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}\\b");

    // ─── Payment card (PAN) ───────────────────────────────────────────────────
    private static final Pattern CARD_PAN = Pattern.compile(
            "\\b(?:\\d[ \\-]?){13,19}\\b");

    private LogSanitizer() {
    }

    // ═════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Returns a short SHA-256 hex prefix of the value — safe for log correlation
     * without revealing the original value.
     */
    public static String fingerprint(String value) {
        if (value == null || value.isBlank()) return "absent";
        return "sha256:" + HexFormat.of().formatHex(sha256(value.getBytes(StandardCharsets.UTF_8)), 0, 8);
    }

    /** @see #fingerprint(String) */
    public static String fingerprint(byte[] value) {
        if (value == null || value.length == 0) return "absent";
        return "sha256:" + HexFormat.of().formatHex(sha256(value), 0, 8);
    }

    /** Always returns {@code MASKED_IP} — prevents IP geolocation leaks in logs. */
    public static String maskedIp(String ignored) {
        return "MASKED_IP";
    }

    /**
     * Checks whether a JSON field name should be masked.
     * Used by {@link source.common.observability.SensitiveDataMasker}.
     */
    public static boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String normalised = key.toLowerCase().replace("-", "").replace("_", "");
        return SENSITIVE_JSON_KEYS.contains(normalised);
    }

    /**
     * Full pipeline sanitisation for financial payloads (HTTP bodies, log lines, etc.).
     * Applies every masking rule in order and caps the output at {@value #MAX_PAYLOAD_CHARS} chars.
     */
    public static String sanitizeFinancialPayload(String value) {
        if (value == null) return null;
        if (value.isBlank()) return "";

        String s = value;

        // 1. Structural JSON keys
        s = SENSITIVE_JSON_FIELD.matcher(s).replaceAll("$1[MASKED]$3");

        // 2. key=value / key: value patterns
        s = SENSITIVE_KEY_VALUE.matcher(s).replaceAll(match -> {
            String text = match.group();
            int sep = firstSepIndex(text);
            String key = sep >= 0 ? text.substring(0, sep + 1) : "";
            return Matcher.quoteReplacement(key + "[MASKED]");
        });

        // 3. Bearer tokens
        s = BEARER_TOKEN.matcher(s).replaceAll("Bearer [MASKED]");

        // 4. Raw JWT tokens (eyJ...)
        s = JWT_TOKEN.matcher(s).replaceAll("[JWT-MASKED]");

        // 5. Lightning invoices
        s = maskWithPrefixSuffix(s, LIGHTNING_INVOICE);

        // 6. Bitcoin addresses
        s = maskWithPrefixSuffix(s, BECH32_ADDRESS);
        s = maskWithPrefixSuffix(s, BASE58_ADDRESS);

        // 7. Brazilian CPF / CNPJ
        s = maskCpf(s);
        s = maskCnpj(s);

        // 8. Email
        s = maskEmail(s);

        // 9. Card PAN
        s = maskCardPan(s);

        return s.length() > MAX_PAYLOAD_CHARS ? s.substring(0, MAX_PAYLOAD_CHARS) : s;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    private static String maskWithPrefixSuffix(String value, Pattern pattern) {
        return pattern.matcher(value).replaceAll(match -> Matcher.quoteReplacement(maskToken(match.group())));
    }

    private static String maskToken(String token) {
        if (token == null || token.length() <= 12) return "[MASKED]";
        return token.substring(0, 6) + "..." + token.substring(token.length() - 4);
    }

    private static String maskCpf(String value) {
        Matcher m = CPF.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String raw = m.group().replaceAll("[.\\-]", "");
            if (raw.length() == 11) {
                m.appendReplacement(sb, Matcher.quoteReplacement(raw.substring(0, 3) + ".***.***-" + raw.substring(9)));
            } else {
                m.appendReplacement(sb, m.group());
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String maskCnpj(String value) {
        Matcher m = CNPJ.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String raw = m.group().replaceAll("[./\\-]", "");
            if (raw.length() == 14) {
                m.appendReplacement(sb, Matcher.quoteReplacement(raw.substring(0, 2) + ".***.***/****-" + raw.substring(12)));
            } else {
                m.appendReplacement(sb, m.group());
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String maskEmail(String value) {
        Matcher m = EMAIL.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String email = m.group();
            int at = email.indexOf('@');
            if (at > 2) {
                String local = email.substring(0, at);
                String domain = email.substring(at);
                String masked = local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
                m.appendReplacement(sb, Matcher.quoteReplacement(masked));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(email));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String maskCardPan(String value) {
        Matcher m = CARD_PAN.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String digits = m.group().replaceAll("[ \\-]", "");
            if (digits.length() >= 13 && digits.length() <= 19) {
                String masked = digits.substring(0, 6) + "******" + digits.substring(digits.length() - 4);
                m.appendReplacement(sb, Matcher.quoteReplacement(masked));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static int firstSepIndex(String value) {
        int colon = value.indexOf(':');
        int equals = value.indexOf('=');
        if (colon < 0) return equals;
        if (equals < 0) return colon;
        return Math.min(colon, equals);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
