package com.kerosene.auth.dto;

import com.kerosene.auth.dto.contracts.UserDTOContract;
import com.kerosene.auth.model.enums.AccountSecurityType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Authentication request/state contract carrying credential proofs and optional security-profile settings. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDTO implements UserDTOContract {

    /** Account name submitted for signup or authentication and constrained to 3..100 characters. */
    @NotBlank(message = "Username cannot be empty")
    @Size(min = 3, max = 100, message = "Username must be between 3 and 100 characters")
    private String username;

    /**
     * Sensitive password/passphrase characters accepted as password or legacy passphrase.
     * The char array limits heap lifetime; Jackson reads it but never serializes it to clients.
     */
    @JsonAlias({ "passphrase" })
    @JsonProperty("password")
    private char[] password;

    /** TOTP seed retained in signup state; it is not serialized into client responses. */
    private String totpSecret;

    /** Short-lived authenticator code accepted only as request input and never serialized in responses. */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String totpCode;

    /** Optional activation voucher associated with signup. */
    private String voucherCode;
    /** Proof-of-work or challenge value bound to signup validation. */
    private String challenge;
    /** Client nonce bound to the challenge proof to limit replay. */
    private String nonce;
    /** Short-lived token issued after preliminary authentication checks. */
    private String preAuthToken;
    /** Signup or authentication session identifier used to bind multi-step operations. */
    private String sessionId;

    /** Requested account security mode; defaults to STANDARD. The platform co-signer secret is never stored here. */
    private AccountSecurityType accountSecurity = AccountSecurityType.STANDARD;

    /** Number of Shamir shares requested for advanced account recovery. */
    private Integer shamirTotalShares;

    /** Minimum Shamir shares required for recovery. */
    private Integer shamirThreshold;

    /** Number of authentication factors required for MULTISIG_2FA. */
    private Integer multisigThreshold;

    /** One-time recovery codes generated during signup and staged in the session store. */
    private java.util.List<String> backupCodes;

    /**
     * Returns the signup recovery codes staged for the registration flow.
     *
     * @return backupCodes value
     */
    public java.util.List<String> getBackupCodes() {
        return backupCodes;
    }

    /**
     * Sets one-time recovery codes generated during signup and staged in the session store.
     *
     * @param backupCodes One-time recovery codes generated during signup and staged in the session store.
     */
    public void setBackupCodes(java.util.List<String> backupCodes) {
        this.backupCodes = backupCodes;
    }

    /**
     * Returns proof-of-work or challenge value bound to signup validation.
     *
     * @return challenge value
     */
    public String getChallenge() {
        return challenge;
    }

    /**
     * Sets proof-of-work or challenge value bound to signup validation.
     *
     * @param challenge Proof-of-work or challenge value bound to signup validation.
     */
    public void setChallenge(String challenge) {
        this.challenge = challenge;
    }

    /**
     * Returns client nonce bound to the challenge proof to limit replay.
     *
     * @return nonce value
     */
    public String getNonce() {
        return nonce;
    }

    /**
     * Sets client nonce bound to the challenge proof to limit replay.
     *
     * @param nonce Client nonce bound to the challenge proof to limit replay.
     */
    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    /**
     * Returns optional activation voucher associated with signup.
     *
     * @return voucherCode value
     */
    public String getVoucherCode() {
        return voucherCode;
    }

    /**
     * Sets optional activation voucher associated with signup.
     *
     * @param voucherCode Optional activation voucher associated with signup.
     */
    public void setVoucherCode(String voucherCode) {
        this.voucherCode = voucherCode;
    }

    /**
     * Returns account name submitted for signup or authentication and constrained to 3..100 characters.
     *
     * @return username value
     */
    @Override
    public String getUsername() {
        return username;
    }

    /**
     * Returns the password character array through the legacy passphrase contract alias.
     * @return sensitive request buffer; the caller must clear it when finished
     */
    @Override
    public char[] getPassphrase() {
        return password;
    }

    /**
     * Returns the request password character array; callers must clear it after authentication.
     *
     * @return password value
     */
    @Override
    public char[] getPassword() {
        return password;
    }

    /**
     * Returns TOTP seed retained in signup state; it is not a client response field.
     *
     * @return totpSecret value
     */
    @Override
    public String getTotpSecret() {
        return totpSecret;
    }

    /**
     * Returns short-lived authenticator code accepted only as request input.
     *
     * @return totpCode value
     */
    @Override
    public String getTotpCode() {
        return totpCode;
    }

    /**
     * Sets account name submitted for signup or authentication and constrained to 3..100 characters.
     *
     * @param username Account name submitted for signup or authentication and constrained to 3..100 characters.
     */
    @Override
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Sets the shared password buffer through the legacy passphrase contract alias.
     * @param passphrase sensitive characters retained for authentication processing
     */
    @Override
    public void setPassphrase(char[] passphrase) {
        this.password = passphrase;
    }

    /**
     * Sets sensitive password/passphrase characters accepted by JSON as password or legacy passphrase.
     *
     * @param password Sensitive password/passphrase characters accepted by JSON as password or legacy passphrase.
     */
    @Override
    public void setPassword(char[] password) {
        this.password = password;
    }

    /**
     * Sets TOTP seed retained in signup state; it is not a client response field.
     *
     * @param totpSecret TOTP seed retained in signup state; it is not a client response field.
     */
    @Override
    public void setTotpSecret(String totpSecret) {
        this.totpSecret = totpSecret;
    }

    /**
     * Sets short-lived authenticator code accepted only as request input.
     *
     * @param totpCode Short-lived authenticator code accepted only as request input.
     */
    @Override
    public void setTotpCode(String totpCode) {
        this.totpCode = totpCode;
    }

    /**
     * Returns signup or authentication session identifier used to bind multi-step operations.
     *
     * @return sessionId value
     */
    @Override
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Sets signup or authentication session identifier used to bind multi-step operations.
     *
     * @param sessionId Signup or authentication session identifier used to bind multi-step operations.
     */
    @Override
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Returns requested account security mode; defaults to STANDARD and excludes the platform co-signer secret.
     *
     * @return accountSecurity value
     */
    public AccountSecurityType getAccountSecurity() {
        return accountSecurity;
    }

    /**
     * Sets requested account security mode; defaults to STANDARD and excludes the platform co-signer secret.
     *
     * @param accountSecurity Requested account security mode; defaults to STANDARD and excludes the platform co-signer secret.
     */
    public void setAccountSecurity(AccountSecurityType accountSecurity) {
        this.accountSecurity = accountSecurity;
    }

    /**
     * Returns number of Shamir shares requested for advanced account recovery.
     *
     * @return shamirTotalShares value
     */
    public Integer getShamirTotalShares() {
        return shamirTotalShares;
    }

    /**
     * Sets number of Shamir shares requested for advanced account recovery.
     *
     * @param shamirTotalShares Number of Shamir shares requested for advanced account recovery.
     */
    public void setShamirTotalShares(Integer shamirTotalShares) {
        this.shamirTotalShares = shamirTotalShares;
    }

    /**
     * Returns minimum Shamir shares required for recovery.
     *
     * @return shamirThreshold value
     */
    public Integer getShamirThreshold() {
        return shamirThreshold;
    }

    /**
     * Sets minimum Shamir shares required for recovery.
     *
     * @param shamirThreshold Minimum Shamir shares required for recovery.
     */
    public void setShamirThreshold(Integer shamirThreshold) {
        this.shamirThreshold = shamirThreshold;
    }

    /**
     * Returns number of authentication factors required for MULTISIG_2FA.
     *
     * @return multisigThreshold value
     */
    public Integer getMultisigThreshold() {
        return multisigThreshold;
    }

    /**
     * Sets number of authentication factors required for MULTISIG_2FA.
     *
     * @param multisigThreshold Number of authentication factors required for MULTISIG_2FA.
     */
    public void setMultisigThreshold(Integer multisigThreshold) {
        this.multisigThreshold = multisigThreshold;
    }

    /**
     * Returns short-lived token issued after preliminary authentication checks.
     *
     * @return preAuthToken value
     */
    @Override
    public String getPreAuthToken() {
        return preAuthToken;
    }

    /**
     * Sets short-lived token issued after preliminary authentication checks.
     *
     * @param preAuthToken Short-lived token issued after preliminary authentication checks.
     */
    @Override
    public void setPreAuthToken(String preAuthToken) {
        this.preAuthToken = preAuthToken;
    }
}
