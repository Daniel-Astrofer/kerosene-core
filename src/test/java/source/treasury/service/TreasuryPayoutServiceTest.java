package source.treasury.service;

import org.junit.jupiter.api.Test;
import source.ledger.entity.SiphonRequest;
import source.ledger.entity.SiphonRequestStatus;
import source.ledger.repository.LedgerEntryRepository;
import source.ledger.repository.SiphonRequestRepository;
import source.transactions.application.externalpayments.ExternalPaymentsMath;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TreasuryPayoutServiceTest {

    private final SiphonRequestRepository repository = mock(SiphonRequestRepository.class);
    private final LedgerEntryRepository ledgerEntryRepository = mock(LedgerEntryRepository.class);
    private final FinancialAuditTrailService auditTrailService = mock(FinancialAuditTrailService.class);
    private final TreasuryPayoutService service = new TreasuryPayoutService(
            repository,
            ledgerEntryRepository,
            auditTrailService,
            new ExternalPaymentsMath("mainnet"),
            "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
            0);

    @Test
    void requestPayoutSnapshotsPendingRevenue() {
        when(repository.findByIdempotencyKey("payout-1")).thenReturn(Optional.empty());
        when(repository.findFirstByStatusInOrderByRequestedAtAsc(anyCollection())).thenReturn(Optional.empty());
        when(ledgerEntryRepository.calculatePlatformProfitPendingUpTo(any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("0.00001000"));
        when(repository.saveAndFlush(any(SiphonRequest.class))).thenAnswer(invocation -> {
            SiphonRequest request = invocation.getArgument(0);
            request.setId(UUID.randomUUID());
            return request;
        });

        SiphonRequest request = service.requestPayout("payout-1", "Admin", null);

        assertEquals(SiphonRequestStatus.REQUESTED, request.getStatus());
        assertEquals(new BigDecimal("0.00001000"), request.getAmount());
        assertEquals("admin", request.getRequestedBy());
        assertEquals(request.getRequestedAt(), request.getRevenueCutoffAt());
        assertEquals(request.getExecutableAfter(), request.getNextAttemptAt());
        verify(auditTrailService).recordBestEffort(
                org.mockito.ArgumentMatchers.eq("TREASURY_PAYOUT_REQUESTED"),
                org.mockito.ArgumentMatchers.eq("TREASURY_PAYOUT"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void requestPayoutRejectsOverlappingActivePayout() {
        SiphonRequest active = new SiphonRequest(new BigDecimal("0.00001000"));
        active.setId(UUID.randomUUID());
        when(repository.findByIdempotencyKey("payout-2")).thenReturn(Optional.empty());
        when(repository.findFirstByStatusInOrderByRequestedAtAsc(anyCollection())).thenReturn(Optional.of(active));

        assertThrows(IllegalStateException.class, () -> service.requestPayout("payout-2", "admin", null));
        verify(ledgerEntryRepository, never()).calculatePlatformProfitPendingUpTo(any(LocalDateTime.class));
    }

    @Test
    void approveAndQueueRequiresStepUpReference() {
        SiphonRequest request = requested();
        when(repository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        assertThrows(IllegalArgumentException.class, () -> service.approveAndQueue(
                request.getId(),
                "admin",
                ""));
    }

    @Test
    void approveAndQueueMovesRequestToQueued() {
        SiphonRequest request = requested();
        when(repository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        SiphonRequest queued = service.approveAndQueue(request.getId(), "Admin", "step-up-ref");

        assertEquals(SiphonRequestStatus.QUEUED, queued.getStatus());
        assertEquals("admin", queued.getApprovedBy());
        assertEquals("step-up-ref", queued.getApprovalReference());
        assertTrue(queued.getNextAttemptAt().compareTo(queued.getExecutableAfter()) >= 0);
    }

    @Test
    void cancelLeavesPayoutTerminalWithoutCollectingFees() {
        SiphonRequest request = requested();
        when(repository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        SiphonRequest cancelled = service.cancel(request.getId(), "Admin", "duplicate");

        assertEquals(SiphonRequestStatus.CANCELLED, cancelled.getStatus());
        assertEquals("admin", cancelled.getCancelledBy());
        assertEquals("duplicate", cancelled.getCancelReason());
        verify(ledgerEntryRepository, never()).markFeesAsCollected();
    }

    private SiphonRequest requested() {
        SiphonRequest request = new SiphonRequest();
        request.setId(UUID.randomUUID());
        request.setAmount(new BigDecimal("0.00001000"));
        request.setDestinationAddress("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh");
        request.setIdempotencyKey("payout-idem");
        request.setRequestedAt(LocalDateTime.now());
        request.setRevenueCutoffAt(request.getRequestedAt());
        request.setExecutableAfter(request.getRequestedAt());
        request.setNextAttemptAt(request.getRequestedAt());
        request.setStatus(SiphonRequestStatus.REQUESTED);
        return request;
    }
}
