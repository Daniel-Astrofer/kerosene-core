package source.treasury.service;

import org.junit.jupiter.api.Test;
import source.ledger.entity.SiphonRequest;
import source.ledger.entity.SiphonRequestStatus;
import source.ledger.repository.LedgerEntryRepository;
import source.ledger.repository.SiphonRequestRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TreasuryPayoutExecutionProcessorTest {

    private final SiphonRequestRepository repository = mock(SiphonRequestRepository.class);
    private final LedgerEntryRepository ledgerEntryRepository = mock(LedgerEntryRepository.class);
    private final TreasuryPayoutRailExecutor railExecutor = mock(TreasuryPayoutRailExecutor.class);
    private final FinancialAuditTrailService auditTrailService = mock(FinancialAuditTrailService.class);
    private final TreasuryPayoutService payoutService = mock(TreasuryPayoutService.class);
    private final TreasuryPayoutExecutionProcessor processor = new TreasuryPayoutExecutionProcessor(
            repository,
            ledgerEntryRepository,
            railExecutor,
            auditTrailService,
            payoutService,
            3);

    @Test
    void settlesPayoutAndCollectsOnlyCutoffFeesAfterProviderSuccess() {
        SiphonRequest request = executingRequest();
        when(repository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(payoutService.normalizeBtc(new BigDecimal("0.00001000"))).thenReturn(new BigDecimal("0.00001000"));
        when(ledgerEntryRepository.calculatePlatformProfitPendingUpTo(request.getRevenueCutoffAt()))
                .thenReturn(new BigDecimal("0.00001000"));
        when(railExecutor.execute(request)).thenReturn(new TreasuryPayoutRailExecutor.ExecutionResult(
                "provider-ref",
                "txid-123",
                "MEMPOOL",
                111L,
                "{}"));
        when(ledgerEntryRepository.markFeesAsCollectedUpTo(request.getRevenueCutoffAt())).thenReturn(2);

        processor.process(request.getId());

        assertEquals(SiphonRequestStatus.SETTLED, request.getStatus());
        assertEquals("provider-ref", request.getProviderReference());
        assertEquals("txid-123", request.getBlockchainTxid());
        assertNull(request.getClaimedBy());
        assertNull(request.getClaimedAt());
        verify(ledgerEntryRepository).markFeesAsCollectedUpTo(request.getRevenueCutoffAt());
        verify(auditTrailService).recordBestEffort(
                eq("TREASURY_PAYOUT_SETTLED"),
                eq("TREASURY_PAYOUT"),
                eq(request.getId().toString()),
                org.mockito.ArgumentMatchers.isNull(),
                eq("txid-123"),
                anyMap());
    }

    @Test
    void failedExecutionRetriesWithoutCollectingFees() {
        SiphonRequest request = executingRequest();
        when(repository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(payoutService.normalizeBtc(new BigDecimal("0.00001000"))).thenReturn(new BigDecimal("0.00001000"));
        when(ledgerEntryRepository.calculatePlatformProfitPendingUpTo(request.getRevenueCutoffAt()))
                .thenReturn(new BigDecimal("0.00001000"));
        when(railExecutor.execute(request)).thenThrow(new IllegalStateException("signer unavailable"));

        processor.process(request.getId());

        assertEquals(SiphonRequestStatus.FAILED, request.getStatus());
        assertEquals(1, request.getAttempts());
        assertTrue(request.isRetryable());
        assertTrue(request.getLastError().contains("TREASURY_PAYOUT_PROVIDER_RETRYABLE"));
        assertNull(request.getClaimedBy());
        verify(ledgerEntryRepository, never()).markFeesAsCollectedUpTo(any(LocalDateTime.class));
    }

    @Test
    void revenueMismatchFailsBeforeProviderCall() {
        SiphonRequest request = executingRequest();
        when(repository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(payoutService.normalizeBtc(new BigDecimal("0.00000500"))).thenReturn(new BigDecimal("0.00000500"));
        when(ledgerEntryRepository.calculatePlatformProfitPendingUpTo(request.getRevenueCutoffAt()))
                .thenReturn(new BigDecimal("0.00000500"));

        processor.process(request.getId());

        assertEquals(SiphonRequestStatus.FAILED, request.getStatus());
        assertFalse(request.isRetryable());
        verify(railExecutor, never()).execute(request);
        verify(ledgerEntryRepository, never()).markFeesAsCollectedUpTo(any(LocalDateTime.class));
    }

    private SiphonRequest executingRequest() {
        LocalDateTime now = LocalDateTime.now();
        SiphonRequest request = new SiphonRequest();
        request.setId(UUID.randomUUID());
        request.setAmount(new BigDecimal("0.00001000"));
        request.setDestinationAddress("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh");
        request.setIdempotencyKey("payout-idem");
        request.setRequestedAt(now.minusMinutes(5));
        request.setRevenueCutoffAt(now.minusMinutes(5));
        request.setExecutableAfter(now.minusMinutes(1));
        request.setNextAttemptAt(now.minusMinutes(1));
        request.setStatus(SiphonRequestStatus.EXECUTING);
        request.setClaimedBy("worker");
        request.setClaimedAt(now.minusSeconds(5));
        request.setApprovalReference("approval-ref");
        return request;
    }
}
