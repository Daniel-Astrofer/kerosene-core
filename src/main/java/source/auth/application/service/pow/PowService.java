package source.auth.application.service.pow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import source.auth.application.service.cache.contracts.RedisServicer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Service
public class PowService {

    private static final Logger log = LoggerFactory.getLogger(PowService.class);

    private final RedisServicer redisServicer;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final String POW_PREFIX = "pow_challenge:";
    private static final int POW_EXPIRATION_SECONDS = 300; // 5 minutes
    private static final String DIFFICULTY_PREFIX = "0000"; // 4 leading hex zeros

    public PowService(RedisServicer redisServicer) {
        this.redisServicer = redisServicer;
    }

    /**
     * Generates a unique PoW challenge and stores it in Redis with an expiration.
     */
    public String generateChallenge() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes) + "-"
                + UUID.randomUUID().toString();

        String key = POW_PREFIX + challenge;
        redisServicer.setValue(key, "valid", POW_EXPIRATION_SECONDS);

        log.info("Generated new PoW challenge. Expires in {}s", POW_EXPIRATION_SECONDS);
        return challenge;
    }

    /**
     * Verifies that the nonce solves the PoW challenge.
     * 1. The challenge must exist in Redis (not expired or already used).
     * 2. SHA-256(challenge + nonce) must start with the defined difficulty prefix.
     * 3. Deletes the challenge to prevent reuse.
     */
    public boolean verifyChallenge(String challenge, String nonce) {
        if (challenge == null || nonce == null || challenge.isEmpty() || nonce.isEmpty()) {
            return false;
        }

        String key = POW_PREFIX + challenge;
        String val = redisServicer.getValue(key);

        if (val == null) {
            log.warn("PoW challenge not found or expired: {}", challenge);
            return false;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = challenge + nonce;
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            String hexHash = bytesToHex(hashBytes);

            if (hexHash.startsWith(DIFFICULTY_PREFIX)) {
                // Legitimate PoW solved. Delete it immediately to prevent replay attacks.
                redisServicer.deleteValue(key);
                log.info("PoW successfully verified and consumed.");
                return true;
            } else {
                log.warn("PoW failed. Hash {} does not start with {}", hexHash, DIFFICULTY_PREFIX);
                return false;
            }

        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not found", e);
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
