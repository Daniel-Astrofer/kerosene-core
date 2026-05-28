package source.transactions.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class WithdrawRequestDTO {
    @NotBlank(message = "idempotencyKey is required")
    @Size(max = 96, message = "idempotencyKey must have at most 96 characters")
    private String idempotencyKey;

    @NotBlank(message = "fromWalletName is required")
    private String fromWalletName;

    @NotBlank(message = "toAddress is required")
    private String toAddress;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.00000001", message = "amount must be greater than zero")
    @DecimalMax(value = "21000000.00000000", message = "amount exceeds the maximum supported BTC amount")
    @Digits(integer = 8, fraction = 8, message = "amount must use BTC precision with at most 8 decimal places")
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

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
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

    public boolean hasPasskeyAssertionResponseJSON() {
        return passkeyAssertionResponseJSON != null && !passkeyAssertionResponseJSON.isBlank();
    }

    public boolean hasConfirmationPassphrase() {
        return confirmationPassphrase != null && !confirmationPassphrase.isBlank();
    }
}
