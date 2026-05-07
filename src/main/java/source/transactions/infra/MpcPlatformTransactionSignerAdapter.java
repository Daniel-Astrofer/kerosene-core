package source.transactions.infra;

import org.springframework.stereotype.Component;
import source.auth.AuthExceptions;
import source.auth.application.service.identityaccess.PlatformTransactionSignerPort;
import source.auth.model.entity.UserDataBase;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

@Component
public class MpcPlatformTransactionSignerAdapter implements PlatformTransactionSignerPort {

    private final MpcSidecarClient mpcClient;

    public MpcPlatformTransactionSignerAdapter(MpcSidecarClient mpcClient) {
        this.mpcClient = mpcClient;
    }

    @Override
    public boolean isAvailable() {
        return mpcClient.isInitialized();
    }

    @Override
    public String sign(UserDataBase user) {
        if (user == null || user.getId() == null) {
            throw new AuthExceptions.AuthValidationException("A persisted user is required for platform MPC signing.");
        }

        String userKeyId = "platform-user-" + user.getId();
        String publicKey = mpcClient.keygen(userKeyId, 2, 3);
        long issuedAt = Instant.now().toEpochMilli();
        byte[] messageHash = platformSignatureHash(user, publicKey, issuedAt);
        byte[] signature = mpcClient.sign(userKeyId, messageHash, publicKey);

        return "mpc-ed25519-v1:"
                + publicKey
                + ":"
                + issuedAt
                + ":"
                + Base64.getEncoder().encodeToString(signature);
    }

    private byte[] platformSignatureHash(UserDataBase user, String publicKey, long issuedAt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("kerosene-platform-cosign:v1".getBytes(StandardCharsets.UTF_8));
            digest.update(longToBytes(user.getId()));
            digest.update(nullSafe(user.getUsername()).getBytes(StandardCharsets.UTF_8));
            digest.update(nullSafe(user.getAccountSecurity()).getBytes(StandardCharsets.UTF_8));
            digest.update(nullSafe(user.getPlatformCosignerSecret()).getBytes(StandardCharsets.UTF_8));
            digest.update(publicKey.getBytes(StandardCharsets.UTF_8));
            digest.update(longToBytes(issuedAt));
            return digest.digest();
        } catch (Exception exception) {
            throw new AuthExceptions.AuthValidationException("Failed to prepare MPC platform signature payload.");
        }
    }

    private byte[] longToBytes(Long value) {
        long numeric = value == null ? 0L : value;
        return java.nio.ByteBuffer.allocate(Long.BYTES).putLong(numeric).array();
    }

    private String nullSafe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
