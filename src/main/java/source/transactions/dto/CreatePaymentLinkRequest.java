package source.transactions.dto;

import java.math.BigDecimal;

/**
 * Request DTO para criar um novo payment link
 */
public class CreatePaymentLinkRequest {
    private BigDecimal amount;
    private String description;

    public CreatePaymentLinkRequest() {
    }

    public CreatePaymentLinkRequest(BigDecimal amount, String description) {
        this.amount = amount;
        this.description = description;
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
}
