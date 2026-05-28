package source.treasury.application.revenue;

import source.treasury.application.chain.ChainContext;
import source.treasury.domain.model.RevenueCollectionResult;

import java.math.BigDecimal;

public class RevenueCollectionContext implements ChainContext {

    private final long networkFeeSats;
    private final long userFeeSats;
    private boolean stop;
    private long profitSats;
    private BigDecimal profitBtc = BigDecimal.ZERO;
    private BigDecimal accumulatedProfitBtc = BigDecimal.ZERO;
    private String merkleRoot;
    private String auditAddress;

    public RevenueCollectionContext(long networkFeeSats, long userFeeSats) {
        this.networkFeeSats = networkFeeSats;
        this.userFeeSats = userFeeSats;
    }

    public long networkFeeSats() {
        return networkFeeSats;
    }

    public long userFeeSats() {
        return userFeeSats;
    }

    public long profitSats() {
        return profitSats;
    }

    public void setProfitSats(long profitSats) {
        this.profitSats = profitSats;
    }

    public BigDecimal profitBtc() {
        return profitBtc;
    }

    public void setProfitBtc(BigDecimal profitBtc) {
        this.profitBtc = profitBtc;
    }

    public BigDecimal accumulatedProfitBtc() {
        return accumulatedProfitBtc;
    }

    public void setAccumulatedProfitBtc(BigDecimal accumulatedProfitBtc) {
        this.accumulatedProfitBtc = accumulatedProfitBtc;
    }

    public String merkleRoot() {
        return merkleRoot;
    }

    public void setMerkleRoot(String merkleRoot) {
        this.merkleRoot = merkleRoot;
    }

    public String auditAddress() {
        return auditAddress;
    }

    public void setAuditAddress(String auditAddress) {
        this.auditAddress = auditAddress;
    }

    public void stop() {
        this.stop = true;
    }

    @Override
    public boolean shouldStop() {
        return stop;
    }

    public RevenueCollectionResult toResult() {
        return new RevenueCollectionResult(
                profitSats,
                profitBtc,
                accumulatedProfitBtc,
                merkleRoot,
                auditAddress);
    }
}
