package source.auth.dto;

import java.util.List;

public class EmergencyRecoveryFinishResponse {

    private String username;
    private List<String> newBackupCodes;

    public EmergencyRecoveryFinishResponse() {
    }

    public EmergencyRecoveryFinishResponse(String username, List<String> newBackupCodes) {
        this.username = username;
        this.newBackupCodes = newBackupCodes;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public List<String> getNewBackupCodes() {
        return newBackupCodes;
    }

    public void setNewBackupCodes(List<String> newBackupCodes) {
        this.newBackupCodes = newBackupCodes;
    }
}
