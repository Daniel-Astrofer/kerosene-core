package source.transactions.dto;

import java.math.BigDecimal;

public class WithdrawRequestDTO {
    private String fromWalletName;
    private String toAddress;
    private BigDecimal amount;
    private String description;
    private String totpCode;
    private String passkeyAssertionResponseJSON;
    private String passkeyAssertionRequestJSON;
    private String confirmationPassphrase;

    public WithdrawRequestDTO() {
    }

    public String getConfirmationPassphrase() {
        return confirmationPassphrase;
    }

    public void setConfirmationPassphrase(String confirmationPassphrase) {
        this.confirmationPassphrase = confirmationPassphrase;
    }

    public String getFromWalletName() {
        return fromWalletName;
    }

    public void setFromWalletName(String fromWalletName) {
        this.fromWalletName = fromWalletName;
    }

    public String getToAddress() {
        return toAddress;
    }

    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTotpCode() {
        return totpCode;
    }

    public void setTotpCode(String totpCode) {
        this.totpCode = totpCode;
    }

    public String getPasskeyAssertionResponseJSON() {
        return passkeyAssertionResponseJSON;
    }

    public void setPasskeyAssertionResponseJSON(String passkeyAssertionResponseJSON) {
        this.passkeyAssertionResponseJSON = passkeyAssertionResponseJSON;
    }

    public String getPasskeyAssertionRequestJSON() {
        return passkeyAssertionRequestJSON;
    }

    public void setPasskeyAssertionRequestJSON(String passkeyAssertionRequestJSON) {
        this.passkeyAssertionRequestJSON = passkeyAssertionRequestJSON;
    }
}
