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

    private String id;
    private BigDecimal amount;
    private String status;
    private LocalDateTime expiresAt;
    private String destinationHash;
    private boolean locked = true;

    public PaymentRequestPublicDTO() {
    }

    public PaymentRequestPublicDTO(InternalPaymentRequestDTO internal) {
        this.id = internal.getId();
        this.amount = internal.getAmount();
        this.status = internal.getStatus();
        this.expiresAt = internal.getExpiresAt();
        this.destinationHash = internal.getDestinationHash();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getDestinationHash() {
        return destinationHash;
    }

    public void setDestinationHash(String destinationHash) {
        this.destinationHash = destinationHash;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }
}
