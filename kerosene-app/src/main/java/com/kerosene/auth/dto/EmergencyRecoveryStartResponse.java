package source.auth.dto;

public class EmergencyRecoveryStartResponse {

    private String recoverySessionId;
    private String otpUri;
    private String passkeyChallenge;
    private long expiresInSeconds;
    private int requiredRecoveryCodes;

    public EmergencyRecoveryStartResponse() {
    }

    public EmergencyRecoveryStartResponse(String recoverySessionId, String otpUri, String passkeyChallenge,
            long expiresInSeconds, int requiredRecoveryCodes) {
        this.recoverySessionId = recoverySessionId;
        this.otpUri = otpUri;
        this.passkeyChallenge = passkeyChallenge;
        this.expiresInSeconds = expiresInSeconds;
        this.requiredRecoveryCodes = requiredRecoveryCodes;
    }

    public String getRecoverySessionId() {
        return recoverySessionId;
    }

    public void setRecoverySessionId(String recoverySessionId) {
        this.recoverySessionId = recoverySessionId;
    }

    public String getOtpUri() {
        return otpUri;
    }

    public void setOtpUri(String otpUri) {
        this.otpUri = otpUri;
    }

    public String getPasskeyChallenge() {
        return passkeyChallenge;
    }

    public void setPasskeyChallenge(String passkeyChallenge) {
        this.passkeyChallenge = passkeyChallenge;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }

    public int getRequiredRecoveryCodes() {
        return requiredRecoveryCodes;
    }

    public void setRequiredRecoveryCodes(int requiredRecoveryCodes) {
        this.requiredRecoveryCodes = requiredRecoveryCodes;
    }
}
