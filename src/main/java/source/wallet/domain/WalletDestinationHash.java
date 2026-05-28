package source.wallet.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public final class WalletDestinationHash {

    private WalletDestinationHash() {
    }

    public static String fromParts(String depositAddress, String passphraseHash, Long walletId) {
        String source = firstNonBlank(
                depositAddress,
                passphraseHash,
                walletId == null ? null : "wallet:" + walletId);

        if (source == null) {
            source = "wallet:unknown";
        }

        return sha256Hex(source);
    }

    public static String normalize(String destinationHash) {
        if (destinationHash == null || destinationHash.isBlank()) {
            return null;
        }
        return destinationHash.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate wallet destination hash", exception);
        }
    }
}
