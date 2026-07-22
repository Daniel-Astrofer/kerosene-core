package source.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Manages the Ed25519 identity of this specific Shard.
 * On first boot, generates a key pair at the configured identity path.
 * Production should point the path at a persistent UID 65532-writable mount.
 */
@Component
public class ShardIdentityManager {

    private static final Logger log = LoggerFactory.getLogger(ShardIdentityManager.class);

    @Value("${shard.identity.path:identity}")
    private String identityPath;

    private KeyPair keyPair;

    @PostConstruct
    public void init() {
        try {
            ensureIdentityExists();
            loadIdentity();
            log.info("[Shard Identity] Initialized node identity. Public Key: {}", getPublicKeyBase64());
        } catch (Exception e) {
            log.error("[Shard Identity] Failed to initialize identity: {}", e.getMessage());
            // In a real sovereign system, failing to load identity should be fatal.
        }
    }

    private void ensureIdentityExists() throws Exception {
        Path dir = Path.of(identityPath);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }

        Path privKeyPath = dir.resolve("shard.priv");
        Path pubKeyPath = dir.resolve("shard.pub");

        if (!Files.exists(privKeyPath)) {
            log.info("[Shard Identity] No identity found. Generating new Ed25519 key pair...");
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            KeyPair kp = kpg.generateKeyPair();

            Files.writeString(privKeyPath, Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()));
            Files.writeString(pubKeyPath, Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()));
            log.info("[Shard Identity] Identity generated and saved to {}", identityPath);
        }
    }

    private void loadIdentity() throws Exception {
        Path dir = Path.of(identityPath);
        String privBase64 = Files.readString(dir.resolve("shard.priv")).trim();
        String pubBase64 = Files.readString(dir.resolve("shard.pub")).trim();

        KeyFactory kf = KeyFactory.getInstance("Ed25519");

        byte[] privBytes = Base64.getDecoder().decode(privBase64);
        byte[] pubBytes = Base64.getDecoder().decode(pubBase64);

        PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
        PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(pubBytes));

        this.keyPair = new KeyPair(pub, priv);
    }

    public String getPublicKeyBase64() {
        KeyPair identity = requireKeyPair();
        return Base64.getEncoder().encodeToString(identity.getPublic().getEncoded());
    }

    /**
     * Stable node identifier derived from the configured Ed25519 public key.
     * This remains stable across restarts only when the identity path is persistent.
     */
    public String getStableNodeId() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(getPublicKeyBase64().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available for stable node identity", e);
        }
    }

    /**
     * Signs a message using the Shard's private key.
     */
    public String sign(String message) {
        try {
            KeyPair identity = requireKeyPair();
            Signature sig = Signature.getInstance("Ed25519");
            sig.initSign(identity.getPrivate());
            sig.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sig.sign());
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign message with Shard identity", e);
        }
    }

    private KeyPair requireKeyPair() {
        if (keyPair == null) {
            throw new IllegalStateException(
                    "[Shard Identity] Identity is not initialized. Check shard.identity.path write permissions: "
                            + identityPath);
        }
        return keyPair;
    }
}
