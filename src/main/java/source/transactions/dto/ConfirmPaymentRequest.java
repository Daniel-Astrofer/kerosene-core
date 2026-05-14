package source.transactions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO para confirmar um pagamento de payment link
 */
public class ConfirmPaymentRequest {
    @NotBlank(message = "idempotencyKey is required")
    @Size(max = 96, message = "idempotencyKey must have at most 96 characters")
    private String idempotencyKey;

    @NotBlank(message = "txid is required")
    private String txid;
    private String fromAddress;

    public ConfirmPaymentRequest() {
    }

    public ConfirmPaymentRequest(String txid, String fromAddress) {
        this.txid = txid;
        this.fromAddress = fromAddress;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getTxid() {
        return txid;
    }

    public void setTxid(String txid) {
        this.txid = txid;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }
}
