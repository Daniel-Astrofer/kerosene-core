package source.auth.dto;

import java.util.List;

public class SignupResponseDTO {
    private String sessionId;
    private String otpUri;
    private List<String> backupCodes;
    private boolean totpOptional;

    public SignupResponseDTO(String sessionId, String otpUri, List<String> backupCodes, boolean totpOptional) {
        this.sessionId = sessionId;
        this.otpUri = otpUri;
        this.backupCodes = backupCodes;
        this.totpOptional = totpOptional;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getOtpUri() {
        return otpUri;
    }

    public void setOtpUri(String otpUri) {
        this.otpUri = otpUri;
    }

    public List<String> getBackupCodes() {
        return backupCodes;
    }

    public void setBackupCodes(List<String> backupCodes) {
        this.backupCodes = backupCodes;
    }

    public boolean isTotpOptional() {
        return totpOptional;
    }

    public void setTotpOptional(boolean totpOptional) {
        this.totpOptional = totpOptional;
    }
}
