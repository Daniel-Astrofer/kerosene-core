package source.transactions.dto;

import java.math.BigDecimal;

/**
 * Request DTO para confirmar um depósito
 */
public class DepositConfirmRequest {
    private String txid;
    private String fromAddress;
    private BigDecimal amount;

    public DepositConfirmRequest() {
    }

    public DepositConfirmRequest(String txid, String fromAddress, BigDecimal amount) {
        this.txid = txid;
        this.fromAddress = fromAddress;
        this.amount = amount;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
