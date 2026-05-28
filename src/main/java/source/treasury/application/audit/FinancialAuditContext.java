package source.treasury.application.audit;

import source.treasury.application.chain.ChainContext;
import source.treasury.domain.model.FinancialAuditResult;
import source.treasury.domain.model.ReserveSnapshot;

import java.math.BigDecimal;

public class FinancialAuditContext implements ChainContext {

    private final boolean solvencyAuditEnforced;
    private final BigDecimal driftTolerance;
    private boolean stop;
    private boolean executed;
    private boolean solvent = true;
    private BigDecimal totalLiabilitiesBtc = BigDecimal.ZERO;
    private ReserveSnapshot reserveSnapshot;
    private String panicReason;

    public FinancialAuditContext(boolean solvencyAuditEnforced, BigDecimal driftTolerance) {
        this.solvencyAuditEnforced = solvencyAuditEnforced;
        this.driftTolerance = driftTolerance;
    }

    public boolean solvencyAuditEnforced() {
        return solvencyAuditEnforced;
    }

    public BigDecimal driftTolerance() {
        return driftTolerance;
    }

    public void markExecuted() {
        this.executed = true;
    }

    public boolean executed() {
        return executed;
    }

    public BigDecimal totalLiabilitiesBtc() {
        return totalLiabilitiesBtc;
    }

    public void setTotalLiabilitiesBtc(BigDecimal totalLiabilitiesBtc) {
        this.totalLiabilitiesBtc = totalLiabilitiesBtc;
    }

    public ReserveSnapshot reserveSnapshot() {
        return reserveSnapshot;
    }

    public void setReserveSnapshot(ReserveSnapshot reserveSnapshot) {
        this.reserveSnapshot = reserveSnapshot;
    }

    public boolean solvent() {
        return solvent;
    }

    public void setSolvent(boolean solvent) {
        this.solvent = solvent;
    }

    public String panicReason() {
        return panicReason;
    }

    public void setPanicReason(String panicReason) {
        this.panicReason = panicReason;
    }

    public void stop() {
        this.stop = true;
    }

    @Override
    public boolean shouldStop() {
        return stop;
    }

    public FinancialAuditResult toResult() {
        return new FinancialAuditResult(executed, solvent, totalLiabilitiesBtc, reserveSnapshot, panicReason);
    }
}
