package source.treasury.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import source.treasury.dto.TreasuryOverviewDTO;
import source.treasury.service.FinancialAuditTrailService;
import source.treasury.service.TreasuryService;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TreasuryControllerTest {

    @Test
    void overviewRecordsSensitiveReadAuditEvent() {
        TreasuryService treasuryService = mock(TreasuryService.class);
        FinancialAuditTrailService auditTrailService = mock(FinancialAuditTrailService.class);
        TreasuryOverviewDTO overview = new TreasuryOverviewDTO(
                new BigDecimal("1.00000000"),
                new BigDecimal("0.50000000"),
                new BigDecimal("0.10000000"),
                new BigDecimal("0.40000000"),
                new BigDecimal("0.01000000"),
                new BigDecimal("0.02000000"),
                new BigDecimal("0.99000000"),
                new BigDecimal("0.38000000"),
                true,
                "HEALTHY");
        when(treasuryService.overview()).thenReturn(overview);

        TreasuryController controller = new TreasuryController(treasuryService, auditTrailService);

        ResponseEntity<TreasuryOverviewDTO> response = controller.overview();

        assertEquals(overview, response.getBody());
        verify(auditTrailService).recordBestEffort(
                eq("TREASURY_OVERVIEW_READ"),
                eq("TREASURY"),
                eq("overview"),
                org.mockito.ArgumentMatchers.isNull(),
                eq("treasury-overview"),
                anyMap());
    }
}
