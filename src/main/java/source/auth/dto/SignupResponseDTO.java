package source.auth.dto;

import java.util.List;

public class SignupResponseDTO {
    private String otpUri;
    private List<String> backupCodes;

    public SignupResponseDTO(String otpUri, List<String> backupCodes) {
        this.otpUri = otpUri;
        this.backupCodes = backupCodes;
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
}
