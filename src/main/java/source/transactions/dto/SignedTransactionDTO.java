package source.transactions.dto;

public class SignedTransactionDTO {

    private String rawTxHex;
    private String description;

    public SignedTransactionDTO() {
    }

    public SignedTransactionDTO(String rawTxHex, String description) {
        this.rawTxHex = rawTxHex;
        this.description = description;
    }

    public String getRawTxHex() {
        return rawTxHex;
    }

    public void setRawTxHex(String rawTxHex) {
        this.rawTxHex = rawTxHex;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
