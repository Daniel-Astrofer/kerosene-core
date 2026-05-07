package source.transactions.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request DTO para criar um novo payment link
 */
public class CreatePaymentLinkRequest {
    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.00000001", message = "amount must be greater than zero")
    @DecimalMax(value = "21000000.00000000", message = "amount exceeds the maximum supported BTC amount")
    @Digits(integer = 8, fraction = 8, message = "amount must use BTC precision with at most 8 decimal places")
    private BigDecimal amount;
    private String description;
    private Integer expiresInMinutes;
    private String visibility;
    private String confirmationMode;
    private Boolean amountLocked = true;
    private String referenceLabel;
    private Map<String, String> metadata = new LinkedHashMap<>();

    public CreatePaymentLinkRequest() {
    }

    public CreatePaymentLinkRequest(
            BigDecimal amount,
            String description,
            Integer expiresInMinutes,
            String visibility,
            String confirmationMode,
            Boolean amountLocked,
            String referenceLabel,
            Map<String, String> metadata) {
        this.amount = amount;
        this.description = description;
        this.expiresInMinutes = expiresInMinutes;
        this.visibility = visibility;
        this.confirmationMode = confirmationMode;
        this.amountLocked = amountLocked;
        this.referenceLabel = referenceLabel;
        if (metadata != null) {
            this.metadata = metadata;
        }
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

    public Integer getExpiresInMinutes() {
        return expiresInMinutes;
    }

    public void setExpiresInMinutes(Integer expiresInMinutes) {
        this.expiresInMinutes = expiresInMinutes;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getConfirmationMode() {
        return confirmationMode;
    }

    public void setConfirmationMode(String confirmationMode) {
        this.confirmationMode = confirmationMode;
    }

    public Boolean getAmountLocked() {
        return amountLocked;
    }

    public void setAmountLocked(Boolean amountLocked) {
        this.amountLocked = amountLocked;
    }

    public String getReferenceLabel() {
        return referenceLabel;
    }

    public void setReferenceLabel(String referenceLabel) {
        this.referenceLabel = referenceLabel;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata != null ? metadata : new LinkedHashMap<>();
    }
}
