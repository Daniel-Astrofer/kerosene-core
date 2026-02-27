package source.auth.dto;

import source.auth.dto.contracts.UserDTOContract;
import source.auth.model.enums.AccountSecurityType;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDTO implements UserDTOContract {

    private String username;
    private String passphrase;
    private String totpSecret;
    private String totpCode;
    private String voucherCode;
    private String challenge;
    private String nonce;

    /**
     * Account security mode requested at signup.
     * Defaults to STANDARD (password + TOTP).
     * The platform co-signer secret is never stored here.
     */
    private AccountSecurityType accountSecurity = AccountSecurityType.STANDARD;

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
    public String getPassphrase() {
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
    public void setPassphrase(String passphrase) {
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
}
