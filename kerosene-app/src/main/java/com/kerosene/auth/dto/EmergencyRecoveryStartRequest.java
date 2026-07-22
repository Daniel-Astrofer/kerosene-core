package source.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

import java.util.List;

public class EmergencyRecoveryStartRequest {

    private String username;

    @JsonProperty(access = Access.WRITE_ONLY)
    private char[] newPassphrase;

    @JsonProperty(access = Access.WRITE_ONLY)
    private List<String> recoveryCodes;

    private String challenge;
    private String nonce;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public char[] getNewPassphrase() {
        return newPassphrase;
    }

    public void setNewPassphrase(char[] newPassphrase) {
        this.newPassphrase = newPassphrase;
    }

    public List<String> getRecoveryCodes() {
        return recoveryCodes;
    }

    public void setRecoveryCodes(List<String> recoveryCodes) {
        this.recoveryCodes = recoveryCodes;
    }

    public String getChallenge() {
        return challenge;
    }

    public void setChallenge(String challenge) {
        this.challenge = challenge;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }
}
