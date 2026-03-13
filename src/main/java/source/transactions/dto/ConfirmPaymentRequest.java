package source.transactions.dto;

/**
 * Request DTO para confirmar um pagamento de payment link
 */
public class ConfirmPaymentRequest {
    private String txid;
    private String fromAddress;

    public ConfirmPaymentRequest() {
    }

    public ConfirmPaymentRequest(String txid, String fromAddress) {
        this.txid = txid;
        this.fromAddress = fromAddress;
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
