package source.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Public-facing view of a payment request.
 *
 * Exposed by the unauthenticated GET /ledger/payment-request/{linkId} endpoint.
 * Deliberately strips all internal identifiers (userId, walletName) that could
 * be used for reconnaissance or account enumeration.
 */
public class PaymentRequestPublicDTO {

    private BigDecimal amount;
    private String status;
    private LocalDateTime expiresAt;

    public PaymentRequestPublicDTO() {
    }

    public PaymentRequestPublicDTO(InternalPaymentRequestDTO internal) {
        this.amount = internal.getAmount();
        this.status = internal.getStatus();
        this.expiresAt = internal.getExpiresAt();
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
