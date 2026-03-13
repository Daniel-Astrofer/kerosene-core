package source.transactions.dto;

import java.math.BigDecimal;
import java.util.List;

public class UnsignedTransactionDTO {

    private String rawTxHex;
    private String txId;
    private List<TransactionInput> inputs;
    private List<TransactionOutput> outputs;
    private BigDecimal totalAmount;
    private Long fee;
    private String fromAddress;
    private String toAddress;

    public static class TransactionInput {
        private String txid;
        private Integer vout;
        private BigDecimal value;
        private String scriptPubKey;

        public TransactionInput() {}

        public TransactionInput(String txid, Integer vout, BigDecimal value, String scriptPubKey) {
            this.txid = txid;
            this.vout = vout;
            this.value = value;
            this.scriptPubKey = scriptPubKey;
        }

        public String getTxid() { return txid; }
        public void setTxid(String txid) { this.txid = txid; }
        public Integer getVout() { return vout; }
        public void setVout(Integer vout) { this.vout = vout; }
        public BigDecimal getValue() { return value; }
        public void setValue(BigDecimal value) { this.value = value; }
        public String getScriptPubKey() { return scriptPubKey; }
        public void setScriptPubKey(String scriptPubKey) { this.scriptPubKey = scriptPubKey; }
    }

    public static class TransactionOutput {
        private String address;
        private BigDecimal value;

        public TransactionOutput() {}

        public TransactionOutput(String address, BigDecimal value) {
            this.address = address;
            this.value = value;
        }

        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public BigDecimal getValue() { return value; }
        public void setValue(BigDecimal value) { this.value = value; }
    }

    public UnsignedTransactionDTO() {}

    public String getRawTxHex() { return rawTxHex; }
    public void setRawTxHex(String rawTxHex) { this.rawTxHex = rawTxHex; }

    public String getTxId() { return txId; }
    public void setTxId(String txId) { this.txId = txId; }

    public List<TransactionInput> getInputs() { return inputs; }
    public void setInputs(List<TransactionInput> inputs) { this.inputs = inputs; }

    public List<TransactionOutput> getOutputs() { return outputs; }
    public void setOutputs(List<TransactionOutput> outputs) { this.outputs = outputs; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public Long getFee() { return fee; }
    public void setFee(Long fee) { this.fee = fee; }

    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }

    public String getToAddress() { return toAddress; }
    public void setToAddress(String toAddress) { this.toAddress = toAddress; }
}
