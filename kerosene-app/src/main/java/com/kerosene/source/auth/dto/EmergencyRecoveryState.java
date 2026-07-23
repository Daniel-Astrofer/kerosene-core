package source.auth.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class EmergencyRecoveryState implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String sessionId;
    private String username;
    private String hashedPassphrase;
    private String encryptedTotpSecret;
    private String passkeyChallenge;
    private List<String> matchedBackupCodeHashes = new ArrayList<>();

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getHashedPassphrase() {
        return hashedPassphrase;
    }

    public void setHashedPassphrase(String hashedPassphrase) {
        this.hashedPassphrase = hashedPassphrase;
    }

    public String getEncryptedTotpSecret() {
        return encryptedTotpSecret;
    }

    public void setEncryptedTotpSecret(String encryptedTotpSecret) {
        this.encryptedTotpSecret = encryptedTotpSecret;
    }

    public String getPasskeyChallenge() {
        return passkeyChallenge;
    }

    public void setPasskeyChallenge(String passkeyChallenge) {
        this.passkeyChallenge = passkeyChallenge;
    }

    public List<String> getMatchedBackupCodeHashes() {
        return matchedBackupCodeHashes;
    }

    public void setMatchedBackupCodeHashes(List<String> matchedBackupCodeHashes) {
        this.matchedBackupCodeHashes = matchedBackupCodeHashes;
    }
}
