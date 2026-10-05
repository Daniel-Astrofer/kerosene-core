package com.kerosene.auth.dto;

import java.util.List;

/** Initial signup response containing session-bound setup material and recovery codes. */
public class SignupResponseDTO {
    /** Opaque identifier for the temporary multi-step signup state. */
    private String sessionId;
    /** Authenticator provisioning URI for the newly generated TOTP seed. */
    private String otpUri;
    /** One-time recovery codes shown to the user during enrollment. */
    private List<String> backupCodes;
    /** Whether policy permits signup completion without verifying TOTP. */
    private boolean totpOptional;

    /** Builds the setup response for the newly created signup session.
     * @param sessionId temporary signup session identifier
     * @param otpUri TOTP provisioning URI
     * @param backupCodes plaintext recovery codes shown once to the user
     * @param totpOptional whether policy allows skipping TOTP verification
     */
    public SignupResponseDTO(String sessionId, String otpUri, List<String> backupCodes, boolean totpOptional) {
        this.sessionId = sessionId;
        this.otpUri = otpUri;
        this.backupCodes = backupCodes;
        this.totpOptional = totpOptional;
    }

    /**
     * Returns opaque identifier for the temporary multi-step signup state.
     * @return sessionId value
     */
    public String getSessionId() { return sessionId; }
    /**
     * Sets opaque identifier for the temporary multi-step signup state.
     * @param sessionId sessionId value
     */
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    /**
     * Returns authenticator provisioning URI for the newly generated TOTP seed.
     * @return otpUri value
     */
    public String getOtpUri() { return otpUri; }
    /**
     * Sets authenticator provisioning URI for the newly generated TOTP seed.
     * @param otpUri otpUri value
     */
    public void setOtpUri(String otpUri) { this.otpUri = otpUri; }

    /**
     * Returns one-time recovery codes shown to the user during enrollment.
     * @return backupCodes value
     */
    public List<String> getBackupCodes() { return backupCodes; }
    /**
     * Sets one-time recovery codes shown to the user during enrollment.
     * @param backupCodes backupCodes value
     */
    public void setBackupCodes(List<String> backupCodes) { this.backupCodes = backupCodes; }

    /**
     * Returns whether policy permits signup completion without verifying TOTP.
     * @return totpOptional value
     */
    public boolean isTotpOptional() { return totpOptional; }
    /**
     * Sets whether policy permits signup completion without verifying TOTP.
     * @param totpOptional totpOptional value
     */
    public void setTotpOptional(boolean totpOptional) { this.totpOptional = totpOptional; }
}
