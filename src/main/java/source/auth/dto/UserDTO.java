package source.auth.dto;

import source.auth.dto.contracts.UserDTOContract;
import source.auth.model.enums.AccountSecurityType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDTO implements UserDTOContract {

    private String username;

    /**
     * Passphrase in char[] to limit heap lifetime.
     * WRITE_ONLY ensures it's read from request but never sent back.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private char[] passphrase;

    /**
     * TOTP seed — stored in Redis during signup, never sent to client.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String totpSecret;

    /** TOTP code is short-lived input. */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String totpCode;

    private String voucherCode;
    private String challenge;
    private String nonce;
    private String preAuthToken;

    /**
     * Defaults to STANDARD (password + TOTP).
     * The platform co-signer secret is never stored here.
     */
    private AccountSecurityType accountSecurity = AccountSecurityType.STANDARD;

    /**
     * Backup codes — generated at signup, stored in Redis.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private java.util.List<String> backupCodes;

    public java.util.List<String> getBackupCodes() {
        return backupCodes;
    }

    public void setBackupCodes(java.util.List<String> backupCodes) {
        this.backupCodes = backupCodes;
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

    public String getVoucherCode() {
        return voucherCode;
    }

    public void setVoucherCode(String voucherCode) {
        this.voucherCode = voucherCode;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public char[] getPassphrase() {
        return passphrase;
    }

    @Override
    public String getTotpSecret() {
        return totpSecret;
    }

    @Override
    public String getTotpCode() {
        return totpCode;
    }

    @Override
    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public void setPassphrase(char[] passphrase) {
        this.passphrase = passphrase;
    }

    @Override
    public void setTotpSecret(String totpSecret) {
        this.totpSecret = totpSecret;
    }

    @Override
    public void setTotpCode(String totpCode) {
        this.totpCode = totpCode;
    }

    public AccountSecurityType getAccountSecurity() {
        return accountSecurity;
    }

    public void setAccountSecurity(AccountSecurityType accountSecurity) {
        this.accountSecurity = accountSecurity;
    }

    @Override
    public String getPreAuthToken() {
        return preAuthToken;
    }

    @Override
    public void setPreAuthToken(String preAuthToken) {
        this.preAuthToken = preAuthToken;
    }
}
