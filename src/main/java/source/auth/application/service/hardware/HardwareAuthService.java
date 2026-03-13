package source.auth.application.service.hardware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import source.auth.application.service.cache.contracts.RedisServicer;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

@Service
public class HardwareAuthService {

    private static final Logger log = LoggerFactory.getLogger(HardwareAuthService.class);
    private final RedisServicer redisService;
    private final SecureRandom secureRandom = new SecureRandom();
    private static final String CHALLENGE_PREFIX = "hw_challenge:";

    public HardwareAuthService(RedisServicer redisService) {
        this.redisService = redisService;
    }

    /**
     * Generates a random challenge (nonce) and stores it in Redis.
     * @param username The username requesting the challenge.
     * @return The hex-encoded or base64 challenge.
     */
    public String generateChallenge(String username) {
        byte[] challenge = new byte[32];
        secureRandom.nextBytes(challenge);
        String challengeHex = bytesToHex(challenge);
        
        // Store in Redis with a 5-minute TTL
        redisService.setValue(CHALLENGE_PREFIX + username, challengeHex, 300);
        
        return challengeHex;
    }

    /**
     * Verifies an Ed25519 signature against a challenge and public key.
     * @param username The username who signed the challenge.
     * @param signatureBase64 The base64-encoded signature.
     * @param publicKeyBase64 The base64-encoded bytes (raw format or X.509) of the public key.
     * @return true if verification succeeds.
     */
    public boolean verifySignature(String username, String signatureBase64, String publicKeyBase64) {
        try {
            String challengeHex = redisService.getValue(CHALLENGE_PREFIX + username);
            if (challengeHex == null) {
                log.warn("Challenge not found or expired for user: {}", username);
                return false;
            }

            byte[] challenge = hexToBytes(challengeHex);
            byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
            byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);

            // In Ed25519, the public key is often just 32 bytes raw.
            // Java's KeyFactory for Ed25519 usually expects X.509 encoding.
            // If the frontend sends raw 32 bytes, we might need a custom approach or wrap it in X.509.
            // For now, let's assume it's X.509 or we use a library if raw is easier.
            // Ed25519 raw to X.509 wrapper:
            // byte[] x509Pub = wrapEd25519RawPublicKey(publicKeyBytes);
            
            Signature ed25519 = Signature.getInstance("Ed25519");
            
            // Try to load as X.509 first
            PublicKey publicKey;
            try {
                KeyFactory kf = KeyFactory.getInstance("EdDSA");
                publicKey = kf.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            } catch (Exception e) {
                // If it fails, maybe it's raw 32 bytes. Ed25519 specific wrapping might be needed.
                // For this implementation, we'll expect X.509 or raw if Java supports it.
                // BouncyCastle helps here if native Java is too strict.
                log.info("Attempting raw Ed25519 key loading...");
                publicKey = loadRawEd25519PublicKey(publicKeyBytes);
            }

            ed25519.initVerify(publicKey);
            ed25519.update(challenge);
            
            boolean verified = ed25519.verify(signatureBytes);
            
            if (verified) {
                redisService.deleteValue(CHALLENGE_PREFIX + username);
            }
            
            return verified;

        } catch (Exception e) {
            log.error("Hardware signature verification failed", e);
            return false;
        }
    }

    private PublicKey loadRawEd25519PublicKey(byte[] rawKey) throws Exception {
        // ASN.1 wrapper for Ed25519 Raw Public Key (32 bytes)
        // 0x30, 0x2a, (Sequence)
        //   0x30, 0x05, (Sequence for AlgorithmIdentifier)
        //     0x06, 0x03, 0x2b, 0x65, 0x70, (OID 1.3.101.112 - Ed25519)
        //   0x03, 0x21, 0x00, (BitString, length 33, 0x00 padding byte)
        //     [32 bytes of raw key]
        byte[] x509 = new byte[12 + rawKey.length];
        System.arraycopy(new byte[]{0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00}, 0, x509, 0, 12);
        System.arraycopy(rawKey, 0, x509, 12, rawKey.length);
        
        KeyFactory kf = KeyFactory.getInstance("EdDSA");
        return kf.generatePublic(new X509EncodedKeySpec(x509));
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }
}
