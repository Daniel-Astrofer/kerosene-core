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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TreasuryPayoutServiceTest {

    private final SiphonRequestRepository repository = mock(SiphonRequestRepository.class);
    private final LedgerEntryRepository ledgerEntryRepository = mock(LedgerEntryRepository.class);
    private final FinancialAuditTrailService auditTrailService = mock(FinancialAuditTrailService.class);
    private final ExternalPaymentsMath math = mock(ExternalPaymentsMath.class);
    private final TreasuryPayoutService service = new TreasuryPayoutService(
            repository,
            ledgerEntryRepository,
            auditTrailService,
            math,
            "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
            0);

    @Test
    void requestPayoutSnapshotsPendingRevenue() {
        when(math.isValidBitcoinAddress(anyString())).thenReturn(true);
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
                eq("TREASURY_PAYOUT_REQUESTED"),
                eq("TREASURY_PAYOUT"),
                any(),
                isNull(),
                any(),
                any());
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
    void requestPayoutRejectsInvalidAddress() {
        when(math.isValidBitcoinAddress(anyString())).thenReturn(false);
        when(repository.findByIdempotencyKey("payout-2")).thenReturn(Optional.empty());
        when(repository.findFirstByStatusInOrderByRequestedAtAsc(anyCollection())).thenReturn(Optional.empty());
        when(ledgerEntryRepository.calculatePlatformProfitPendingUpTo(any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("0.00001000"));

        assertThrows(IllegalStateException.class, () -> service.requestPayout("payout-2", "admin", null));
    }

    @Test
    void requestPayoutReturnsExistingOnIdempotencyKeyMatch() {
        SiphonRequest existing = new SiphonRequest(new BigDecimal("0.00001000"));
        existing.setId(UUID.randomUUID());
        existing.setIdempotencyKey("payout-1");
        when(repository.findByIdempotencyKey("payout-1")).thenReturn(Optional.of(existing));

        SiphonRequest request = service.requestPayout("payout-1", "Admin", null);
        assertEquals(existing.getId(), request.getId());
    }

    @Test
    void requestPayoutFailsOnNoProfit() {
        when(math.isValidBitcoinAddress(anyString())).thenReturn(true);
        when(repository.findByIdempotencyKey("payout-1")).thenReturn(Optional.empty());
        when(repository.findFirstByStatusInOrderByRequestedAtAsc(anyCollection())).thenReturn(Optional.empty());
        when(ledgerEntryRepository.calculatePlatformProfitPendingUpTo(any(LocalDateTime.class)))
                .thenReturn(BigDecimal.ZERO);

        assertThrows(IllegalArgumentException.class, () -> service.requestPayout("payout-1", "admin", null));
    }

    @Test
    void requestPayoutFailsOnMismatchedAmount() {
        when(math.isValidBitcoinAddress(anyString())).thenReturn(true);
        when(repository.findByIdempotencyKey("payout-1")).thenReturn(Optional.empty());
        when(repository.findFirstByStatusInOrderByRequestedAtAsc(anyCollection())).thenReturn(Optional.empty());
        when(ledgerEntryRepository.calculatePlatformProfitPendingUpTo(any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("0.00002000"));

        assertThrows(IllegalArgumentException.class, () -> service.requestPayout("payout-1", "admin", new BigDecimal("0.00001000")));
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
    void approveAndQueueHandlesAlreadySettled() {
        SiphonRequest request = requested();
        request.setStatus(SiphonRequestStatus.SETTLED);
        when(repository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        SiphonRequest approved = service.approveAndQueue(request.getId(), "Admin", "step-up-ref");
        assertEquals(SiphonRequestStatus.SETTLED, approved.getStatus());
    }

    @Test
    void approveAndQueueFailsIfCancelled() {
        SiphonRequest request = requested();
        request.setStatus(SiphonRequestStatus.CANCELLED);
        when(repository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        assertThrows(IllegalStateException.class, () -> service.approveAndQueue(request.getId(), "Admin", "step-up-ref"));
    }

    @Test
    void approveAndQueueFailsIfExecuting() {
        SiphonRequest request = requested();
        request.setStatus(SiphonRequestStatus.EXECUTING);
        when(repository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        assertThrows(IllegalStateException.class, () -> service.approveAndQueue(request.getId(), "Admin", "step-up-ref"));
    }

    @Test
    void approveAndQueueReturnsIfAlreadyQueued() {
        SiphonRequest request = requested();
        request.setStatus(SiphonRequestStatus.QUEUED);
        when(repository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        SiphonRequest queued = service.approveAndQueue(request.getId(), "Admin", "step-up-ref");
        assertEquals(SiphonRequestStatus.QUEUED, queued.getStatus());
    }

    @Test
    void approveAndQueueRequeuesFailedIfRetryable() {
        SiphonRequest request = requested();
        request.setStatus(SiphonRequestStatus.FAILED);
        request.setRetryable(true);
        when(repository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        SiphonRequest queued = service.approveAndQueue(request.getId(), "Admin", "step-up-ref");
        assertEquals(SiphonRequestStatus.QUEUED, queued.getStatus());
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

    @Test
    void cancelFailsIfSettled() {
        SiphonRequest request = requested();
        request.setStatus(SiphonRequestStatus.SETTLED);
        when(repository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        assertThrows(IllegalStateException.class, () -> service.cancel(request.getId(), "Admin", "duplicate"));
    }

    @Test
    void cancelFailsIfExecuting() {
        SiphonRequest request = requested();
        request.setStatus(SiphonRequestStatus.EXECUTING);
        when(repository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        assertThrows(IllegalStateException.class, () -> service.cancel(request.getId(), "Admin", "duplicate"));
    }

    @Test
    void cancelReturnsIfAlreadyCancelled() {
        SiphonRequest request = requested();
        request.setStatus(SiphonRequestStatus.CANCELLED);
        when(repository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        SiphonRequest cancelled = service.cancel(request.getId(), "Admin", "duplicate");
        assertEquals(SiphonRequestStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void claimDueReturnsClaimedRequests() {
        SiphonRequest request = requested();
        request.setStatus(SiphonRequestStatus.QUEUED);
        when(repository.findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByRequestedAtAsc(anyCollection(), any(LocalDateTime.class)))
                .thenReturn(List.of(request));
        when(repository.claimDue(any(), anyCollection(), any(), any(), any(), any(), anyString()))
                .thenReturn(1);
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));

        List<SiphonRequest> claimed = service.claimDue("worker1");
        assertEquals(1, claimed.size());
        assertEquals(request.getId(), claimed.get(0).getId());
    }

    @Test
    void claimDueIgnoresUnclaimedRequests() {
        SiphonRequest request = requested();
        request.setStatus(SiphonRequestStatus.QUEUED);
        when(repository.findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByRequestedAtAsc(anyCollection(), any(LocalDateTime.class)))
                .thenReturn(List.of(request));
        when(repository.claimDue(any(), anyCollection(), any(), any(), any(), any(), anyString()))
                .thenReturn(0);

        List<SiphonRequest> claimed = service.claimDue("worker1");
        assertTrue(claimed.isEmpty());
    }

    @Test
    void backlogSnapshotReturnsCorrectSnapshot() {
        when(repository.countByStatusIn(anyCollection())).thenReturn(5L);
        when(repository.maxAttemptsByStatusIn(anyCollection())).thenReturn(3);

        TreasuryPayoutService.PayoutBacklogSnapshot snapshot = service.backlogSnapshot();
        assertEquals(5L, snapshot.backlog());
        assertEquals(3, snapshot.maxAttempts());
    }

    @Test
    void btcToSatsWorksCorrectly() {
        assertEquals(1000L, service.btcToSats(new BigDecimal("0.00001000")));
    }

    @Test
    void normalizeBtcWorksCorrectly() {
        assertEquals(new BigDecimal("0.00001000"), service.normalizeBtc(new BigDecimal("0.00001")));
        assertEquals(new BigDecimal("0.00000000"), service.normalizeBtc(null));
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
