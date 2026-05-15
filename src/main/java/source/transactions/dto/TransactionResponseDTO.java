package source.transactions.dto;

import java.math.BigDecimal;

public class TransactionResponseDTO {

    private String txid;
    private String status;
    private Long feeSatoshis;
    private BigDecimal amountReceived;

    public TransactionResponseDTO() {
    }

    public TransactionResponseDTO(String txid, String status, Long feeSatoshis) {
        this.txid = txid;
        this.status = status;
        this.feeSatoshis = feeSatoshis;
        this.amountReceived = BigDecimal.ZERO;
    }

    public TransactionResponseDTO(String txid, String status, Long feeSatoshis, BigDecimal amountReceived) {
        this.txid = txid;
        this.status = status;
        this.feeSatoshis = feeSatoshis;
        this.amountReceived = amountReceived;
    }

    public String getTxid() {
        return txid;
    }

    public void setTxid(String txid) {
        this.txid = txid;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getFeeSatoshis() {
        return feeSatoshis;
    }

    public void setFeeSatoshis(Long feeSatoshis) {
        this.feeSatoshis = feeSatoshis;
    }

    public BigDecimal getAmountReceived() {
        return amountReceived;
    }

    public void setAmountReceived(BigDecimal amountReceived) {
        this.amountReceived = amountReceived;
    }
}
