package source.common.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

public final class IdempotencyKeyBuilder {

    private IdempotencyKeyBuilder() {
    }

    public static String build(String namespace, String... parts) {
        String joined = Arrays.stream(parts)
                .map(part -> part == null ? "" : part)
                .reduce((left, right) -> left + ":" + right)
                .orElse("");
        return namespace + ":" + sha256Hex(joined);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
