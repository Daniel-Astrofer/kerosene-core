package com.kerosene.auth.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/** Input for confirming the TOTP factor during a multi-step signup session. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SignupTotpVerifyRequestDTO {

    /** Signup session identifier required to bind the TOTP attempt to onboarding state. */
    @NotBlank(message = "Signup sessionId required")
    private String sessionId;

    /** Authenticator code presented for signup verification. */
    private String totpCode;

    /**
     * Creates an empty request for Jackson field binding.
     */
    public SignupTotpVerifyRequestDTO() {
    }

    /**
     * Creates the request from the JSON fields used by signup clients.
     * @param sessionId active signup session identifier
     * @param totpCode authenticator code to verify
     */
    @JsonCreator
    public SignupTotpVerifyRequestDTO(
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("totpCode") String totpCode) {
        this.sessionId = sessionId;
        this.totpCode = totpCode;
    }

    /**
     * Returns the signup session identifier.
     * @return session ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Sets the signup session identifier.
     * @param sessionId active session ID
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Returns the submitted authenticator code.
     * @return TOTP code
     */
    public String getTotpCode() {
        return totpCode;
    }

    /**
     * Sets the authenticator code to verify.
     * @param totpCode six-digit code
     */
    public void setTotpCode(String totpCode) {
        this.totpCode = totpCode;
    }
}
