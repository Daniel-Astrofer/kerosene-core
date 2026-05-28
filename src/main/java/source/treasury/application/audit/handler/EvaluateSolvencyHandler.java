package source.treasury.application.audit.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import source.treasury.application.audit.AbstractFinancialAuditHandler;
import source.treasury.application.audit.FinancialAuditContext;
import source.treasury.domain.model.ReserveSnapshot;

import java.math.BigDecimal;

@Component
public class EvaluateSolvencyHandler extends AbstractFinancialAuditHandler {

    private static final Logger log = LoggerFactory.getLogger(EvaluateSolvencyHandler.class);

    @Override
    protected void doHandle(FinancialAuditContext context) {
        ReserveSnapshot reserves = context.reserveSnapshot();
        BigDecimal totalAssets = reserves.totalAssetsBtc();

        log.info("[Financial Audit] Liabilities: {} BTC | Total Assets: {} BTC (Hot={}, WalletXPUB={}, TreasuryXPUB={}, Lightning={})",
                context.totalLiabilitiesBtc().toPlainString(),
                totalAssets.toPlainString(),
                reserves.hotWalletBtc().toPlainString(),
                reserves.walletMonitoredOnchainBtc().toPlainString(),
                reserves.treasuryXpubOnchainBtc().toPlainString(),
                reserves.lightningBtc().toPlainString());

        boolean solvent = context.totalLiabilitiesBtc()
                .compareTo(totalAssets.add(context.driftTolerance())) <= 0;
        context.setSolvent(solvent);

        if (!solvent) {
            context.setPanicReason("INSOLVENCY_DETECTED: Ledger liabilities exceed physical reserves!");
            return;
        }

        log.info("[Financial Audit] Integrity Verified: Reserves are sufficient.");
    }
}
