package source.treasury.entity;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

public class HmacIntegrityListener {

    private static final String SECRET_KEY = System.getenv("HMAC_SECRET_KEY") != null ?
            System.getenv("HMAC_SECRET_KEY") : "default-dev-secret-1234567890abcdef";

    @PrePersist
    @PreUpdate
    public void signRevenue(PlatformRevenue revenue) {
        if (revenue.getUpdatedAt() == null) {
            revenue.setUpdatedAt(LocalDateTime.now());
        }

        String idStr = revenue.getId() != null ? String.valueOf(revenue.getId()) : "1";

        // Requirement: ID + ACCUMULATED_PROFIT + UPDATED_AT + SECRET_KEY (implicit in HMAC)
        String dataToHash = idStr + ":" +
                            revenue.getAccumulatedProfit().toPlainString() + ":" +
                            revenue.getUpdatedAt().toEpochSecond(ZoneOffset.UTC);

        revenue.setHmacSha256(calculateHmac(dataToHash, SECRET_KEY));
    }

    public static boolean validateIntegrity(PlatformRevenue revenue) {
        if (revenue.getHmacSha256() == null || revenue.getUpdatedAt() == null) return false;

        String idStr = revenue.getId() != null ? String.valueOf(revenue.getId()) : "1";
        String dataToHash = idStr + ":" +
                            revenue.getAccumulatedProfit().toPlainString() + ":" +
                            revenue.getUpdatedAt().toEpochSecond(ZoneOffset.UTC);

        String calculatedHmac = calculateHmac(dataToHash, SECRET_KEY);
        return calculatedHmac.equals(revenue.getHmacSha256());
    }

    private static String calculateHmac(String data, String key) {
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("CRITICAL: Failed to calculate HMAC for PlatformRevenue integrity check", e);
        }
    }
}
