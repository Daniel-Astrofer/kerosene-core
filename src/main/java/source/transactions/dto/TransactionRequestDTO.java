package source.transactions.dto;

import java.math.BigDecimal;

public class TransactionRequestDTO {

    private String fromAddress;
    private String toAddress;
    private BigDecimal amount;
    private Long feeSatoshis;

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getToAddress() {
        return toAddress;
    }

    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Long getFeeSatoshis() {
        return feeSatoshis;
    }

    public void setFeeSatoshis(Long feeSatoshis) {
        this.feeSatoshis = feeSatoshis;
    }
}
