package source.sovereign.quorum;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HexFormat;
import java.util.Map;

@Service
public class QuorumAttestationService {

    public static final String SHARD_ID_HEADER = "X-Shard-Id";
    public static final String TIMESTAMP_HEADER = "X-Shard-Timestamp";
    public static final String NONCE_HEADER = "X-Shard-Nonce";
    public static final String SIGNATURE_HEADER = "X-Shard-Signature";

    private final String shardId;
    private final byte[] key;
    private final long maxSkewMs;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, Long> seenNonces = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public QuorumAttestationService(
            @Value("${REGION:${SHARD_ID:local}}") String shardId,
            @Value("${shard.attestation.secret:}") String secret,
            @Value("${quorum.shard.attestation-max-skew-ms:30000}") long maxSkewMs) {
        this(shardId, secret, maxSkewMs, Clock.systemUTC());
    }

    QuorumAttestationService(String shardId, String secret, long maxSkewMs, Clock clock) {
        this.shardId = hasText(shardId) ? shardId.trim() : "local";
        this.key = decodeSecret(secret);
        this.maxSkewMs = Math.max(1_000, maxSkewMs);
        this.clock = clock;
    }

    public Map<String, String> signedHeaders(String path, String txHash) {
        ensureConfigured();
        String timestamp = Long.toString(clock.millis());
        String nonce = nextNonce();
        return Map.of(
                SHARD_ID_HEADER, shardId,
                TIMESTAMP_HEADER, timestamp,
                NONCE_HEADER, nonce,
                SIGNATURE_HEADER, sign(path, txHash, shardId, timestamp, nonce));
    }

    public boolean verify(
            String path,
            String txHash,
            String remoteShardId,
            String timestamp,
            String nonce,
            String signature) {
        ensureConfigured();
        if (!hasText(path) || !hasText(txHash) || !hasText(remoteShardId)
                || !hasText(timestamp) || !hasText(nonce) || !hasText(signature)) {
            return false;
        }

        long parsedTimestamp;
        try {
            parsedTimestamp = Long.parseLong(timestamp);
        } catch (NumberFormatException ignored) {
            return false;
        }

        long skew = Math.abs(clock.millis() - parsedTimestamp);
        if (skew > maxSkewMs) {
            return false;
        }

        cleanupExpiredNonces();
        String expected = sign(path, txHash, remoteShardId, timestamp, nonce);
        boolean signatureMatches = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
        if (!signatureMatches) {
            return false;
        }

        long nonceExpiry = parsedTimestamp + maxSkewMs;
        String nonceKey = remoteShardId.trim() + ":" + nonce.trim();
        Long previous = seenNonces.putIfAbsent(nonceKey, nonceExpiry);
        return previous == null || previous < clock.millis();
    }

    public boolean isConfigured() {
        return key.length > 0;
    }

    private String sign(String path, String txHash, String remoteShardId, String timestamp, String nonce) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical(path, txHash, remoteShardId, timestamp, nonce)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign quorum attestation.", exception);
        }
    }

    private String canonical(String path, String txHash, String remoteShardId, String timestamp, String nonce) {
        return normalizePath(path) + "\n"
                + txHash.trim() + "\n"
                + remoteShardId.trim() + "\n"
                + timestamp.trim() + "\n"
                + nonce.trim();
    }

    private String normalizePath(String path) {
        String clean = path != null ? path.trim() : "/";
        return clean.startsWith("/") ? clean : "/" + clean;
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("shard.attestation.secret is required for quorum attestation.");
        }
    }

    private byte[] decodeSecret(String secret) {
        if (!hasText(secret)) {
            return new byte[0];
        }
        String clean = secret.trim();
        try {
            byte[] decoded = Base64.getDecoder().decode(clean);
            if (decoded.length >= 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // Plain-text secrets are accepted for local docker; production safety checks still require configuration.
        }
        return clean.getBytes(StandardCharsets.UTF_8);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String nextNonce() {
        byte[] bytes = new byte[18];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void cleanupExpiredNonces() {
        long now = clock.millis();
        seenNonces.entrySet().removeIf(entry -> entry.getValue() < now);
    }
}
