package source.transactions.dto;

/**
 * DTO para solicitação de broadcast de transação
 */
public class BroadcastTransactionDTO {
    private String rawTxHex;
    private String toAddress;
    private java.math.BigDecimal amount;
    private String message;

    public BroadcastTransactionDTO() {
    }

    public BroadcastTransactionDTO(String rawTxHex, String toAddress, java.math.BigDecimal amount, String message) {
        this.rawTxHex = rawTxHex;
        this.toAddress = toAddress;
        this.amount = amount;
        this.message = message;
    }

    public String getRawTxHex() {
        return rawTxHex;
    }

    public void setRawTxHex(String rawTxHex) {
        this.rawTxHex = rawTxHex;
    }

    public String getToAddress() {
        return toAddress;
    }

    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }

    public java.math.BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(java.math.BigDecimal amount) {
        this.amount = amount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
