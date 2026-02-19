package source.transactions.dto;

/**
 * DTO para solicitação de broadcast de transação
 */
public class BroadcastTransactionDTO {
    private String rawTxHex;

    public BroadcastTransactionDTO() {
    }

    public BroadcastTransactionDTO(String rawTxHex) {
        this.rawTxHex = rawTxHex;
    }

    public String getRawTxHex() {
        return rawTxHex;
    }

    public void setRawTxHex(String rawTxHex) {
        this.rawTxHex = rawTxHex;
    }
}
