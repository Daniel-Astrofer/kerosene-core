package source.transactions.dto;

import java.math.BigDecimal;

public class TransactionResponseDTO {

    private String txid;
    private String status;
    private Long feeSatoshis;
    private BigDecimal amountReceived;
    private String sender;
    private String receiver;
    private String context;

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

    public TransactionResponseDTO(String txid, String status, Long feeSatoshis, BigDecimal amountReceived,
            String sender, String receiver, String context) {
        this.txid = txid;
        this.status = status;
        this.feeSatoshis = feeSatoshis;
        this.amountReceived = amountReceived;
        this.sender = sender;
        this.receiver = receiver;
        this.context = context;
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

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }
}
