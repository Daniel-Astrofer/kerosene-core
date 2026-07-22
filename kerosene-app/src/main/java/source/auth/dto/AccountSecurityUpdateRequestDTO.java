package source.auth.dto;

import source.auth.model.enums.AccountSecurityType;

public class AccountSecurityUpdateRequestDTO {

    private AccountSecurityType accountSecurity = AccountSecurityType.STANDARD;
    private Integer shamirTotalShares;
    private Integer shamirThreshold;
    private Integer multisigThreshold;

    public AccountSecurityType getAccountSecurity() {
        return accountSecurity;
    }

    public void setAccountSecurity(AccountSecurityType accountSecurity) {
        this.accountSecurity = accountSecurity;
    }

    public Integer getShamirTotalShares() {
        return shamirTotalShares;
    }

    public void setShamirTotalShares(Integer shamirTotalShares) {
        this.shamirTotalShares = shamirTotalShares;
    }

    public Integer getShamirThreshold() {
        return shamirThreshold;
    }

    public void setShamirThreshold(Integer shamirThreshold) {
        this.shamirThreshold = shamirThreshold;
    }

    public Integer getMultisigThreshold() {
        return multisigThreshold;
    }

    public void setMultisigThreshold(Integer multisigThreshold) {
        this.multisigThreshold = multisigThreshold;
    }
}
