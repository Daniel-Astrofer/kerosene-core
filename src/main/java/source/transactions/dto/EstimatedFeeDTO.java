package source.transactions.dto;

import java.math.BigDecimal;

public class EstimatedFeeDTO {

    private Long fastSatoshisPerByte;      // Fee para confirmação rápida (0-30 min)
    private Long standardSatoshisPerByte;  // Fee padrão (30-60 min)
    private Long slowSatoshisPerByte;      // Fee lenta (1+ horas)
    
    private BigDecimal estimatedFastBtc;
    private BigDecimal estimatedStandardBtc;
    private BigDecimal estimatedSlowBtc;
    
    private BigDecimal amountReceived;     // Quanto o destinatário receberá depois do desconto
    private BigDecimal totalToSend;        // Total que remetente precisa ter (amount + fee)

    public EstimatedFeeDTO() {
    }

    public EstimatedFeeDTO(Long fastSatoshisPerByte, Long standardSatoshisPerByte, 
                           Long slowSatoshisPerByte, BigDecimal amountReceived, 
                           BigDecimal totalToSend) {
        this.fastSatoshisPerByte = fastSatoshisPerByte;
        this.standardSatoshisPerByte = standardSatoshisPerByte;
        this.slowSatoshisPerByte = slowSatoshisPerByte;
        this.amountReceived = amountReceived;
        this.totalToSend = totalToSend;
    }

    public Long getFastSatoshisPerByte() {
        return fastSatoshisPerByte;
    }

    public void setFastSatoshisPerByte(Long fastSatoshisPerByte) {
        this.fastSatoshisPerByte = fastSatoshisPerByte;
    }

    public Long getStandardSatoshisPerByte() {
        return standardSatoshisPerByte;
    }

    public void setStandardSatoshisPerByte(Long standardSatoshisPerByte) {
        this.standardSatoshisPerByte = standardSatoshisPerByte;
    }

    public Long getSlowSatoshisPerByte() {
        return slowSatoshisPerByte;
    }

    public void setSlowSatoshisPerByte(Long slowSatoshisPerByte) {
        this.slowSatoshisPerByte = slowSatoshisPerByte;
    }

    public BigDecimal getEstimatedFastBtc() {
        return estimatedFastBtc;
    }

    public void setEstimatedFastBtc(BigDecimal estimatedFastBtc) {
        this.estimatedFastBtc = estimatedFastBtc;
    }

    public BigDecimal getEstimatedStandardBtc() {
        return estimatedStandardBtc;
    }

    public void setEstimatedStandardBtc(BigDecimal estimatedStandardBtc) {
        this.estimatedStandardBtc = estimatedStandardBtc;
    }

    public BigDecimal getEstimatedSlowBtc() {
        return estimatedSlowBtc;
    }

    public void setEstimatedSlowBtc(BigDecimal estimatedSlowBtc) {
        this.estimatedSlowBtc = estimatedSlowBtc;
    }

    public BigDecimal getAmountReceived() {
        return amountReceived;
    }

    public void setAmountReceived(BigDecimal amountReceived) {
        this.amountReceived = amountReceived;
    }

    public BigDecimal getTotalToSend() {
        return totalToSend;
    }

    public void setTotalToSend(BigDecimal totalToSend) {
        this.totalToSend = totalToSend;
    }
}
