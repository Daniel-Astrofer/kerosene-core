package source.treasury.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import source.treasury.dto.TreasuryOverviewDTO;
import source.treasury.service.FinancialAuditTrailService;
import source.treasury.service.TreasuryService;

import java.util.Map;

@RestController
@RequestMapping("/treasury")
@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMIN')")
public class TreasuryController {

    private final TreasuryService treasuryService;
    private final FinancialAuditTrailService auditTrailService;

    public TreasuryController(
            TreasuryService treasuryService,
            FinancialAuditTrailService auditTrailService) {
        this.treasuryService = treasuryService;
        this.auditTrailService = auditTrailService;
    }

    @GetMapping("/overview")
    public ResponseEntity<TreasuryOverviewDTO> overview() {
        TreasuryOverviewDTO overview = treasuryService.overview();
        auditTrailService.recordBestEffort(
                "TREASURY_OVERVIEW_READ",
                "TREASURY",
                "overview",
                null,
                "treasury-overview",
                Map.of(
                        "liquidityState", overview.liquidityState(),
                        "lightningSendsAllowed", overview.lightningSendsAllowed(),
                        "onchainAvailablePositive", overview.availableOnchainBtc().signum() > 0,
                        "lightningAvailablePositive", overview.availableLightningBtc().signum() > 0));
        return ResponseEntity.ok(overview);
    }
}
