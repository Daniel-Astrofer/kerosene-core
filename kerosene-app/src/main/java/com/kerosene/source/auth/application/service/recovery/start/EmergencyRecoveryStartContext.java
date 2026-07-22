package source.auth.application.service.recovery.start;

import java.util.List;

import source.auth.dto.EmergencyRecoveryStartRequest;
import source.auth.model.entity.UserDataBase;

public class EmergencyRecoveryStartContext {

    private final EmergencyRecoveryStartRequest request;
    private final String clientFingerprint;
    private String normalizedUsername;
    private List<String> normalizedRecoveryCodes = List.of();
    private UserDataBase user;
    private List<String> matchedRecoveryCodeHashes = List.of();

    public EmergencyRecoveryStartContext(EmergencyRecoveryStartRequest request, String clientFingerprint) {
        this.request = request;
        this.clientFingerprint = clientFingerprint;
    }

    public EmergencyRecoveryStartRequest request() {
        return request;
    }

    public String clientFingerprint() {
        return clientFingerprint;
    }

    public String normalizedUsername() {
        return normalizedUsername;
    }

    public void setNormalizedUsername(String normalizedUsername) {
        this.normalizedUsername = normalizedUsername;
    }

    public List<String> normalizedRecoveryCodes() {
        return normalizedRecoveryCodes;
    }

    public void setNormalizedRecoveryCodes(List<String> normalizedRecoveryCodes) {
        this.normalizedRecoveryCodes = normalizedRecoveryCodes;
    }

    public UserDataBase user() {
        return user;
    }

    public void setUser(UserDataBase user) {
        this.user = user;
    }

    public List<String> matchedRecoveryCodeHashes() {
        return matchedRecoveryCodeHashes;
    }

    public void setMatchedRecoveryCodeHashes(List<String> matchedRecoveryCodeHashes) {
        this.matchedRecoveryCodeHashes = matchedRecoveryCodeHashes;
    }
}
