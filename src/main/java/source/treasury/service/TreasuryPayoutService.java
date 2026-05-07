package source.treasury.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.common.infra.logging.LogSanitizer;
import source.ledger.entity.SiphonRequest;
import source.ledger.entity.SiphonRequestStatus;
import source.ledger.repository.LedgerEntryRepository;
import source.ledger.repository.SiphonRequestRepository;
import source.transactions.application.externalpayments.ExternalPaymentsMath;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class TreasuryPayoutService {

    private static final List<SiphonRequestStatus> ACTIVE_STATUSES = List.of(
            SiphonRequestStatus.REQUESTED,
            SiphonRequestStatus.APPROVED,
            SiphonRequestStatus.QUEUED,
            SiphonRequestStatus.EXECUTING,
            SiphonRequestStatus.FAILED);
    private static final List<SiphonRequestStatus> DUE_STATUSES = List.of(
            SiphonRequestStatus.QUEUED,
            SiphonRequestStatus.FAILED);
    private static final List<SiphonRequestStatus> CLAIM_CANDIDATE_STATUSES = List.of(
            SiphonRequestStatus.QUEUED,
            SiphonRequestStatus.FAILED,
            SiphonRequestStatus.EXECUTING);
    private static final Duration STALE_CLAIM_AFTER = Duration.ofMinutes(10);
    private static final BigDecimal SATS_PER_BTC = new BigDecimal("100000000");

    private final SiphonRequestRepository repository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final FinancialAuditTrailService auditTrailService;
    private final ExternalPaymentsMath externalPaymentsMath;
    private final String ownerDestinationAddress;
    private final Duration executableDelay;

    public TreasuryPayoutService(
            SiphonRequestRepository repository,
            LedgerEntryRepository ledgerEntryRepository,
            FinancialAuditTrailService auditTrailService,
            ExternalPaymentsMath externalPaymentsMath,
            @Value("${treasury.payout.destination-address:bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh}") String ownerDestinationAddress,
            @Value("${treasury.payout.executable-delay-hours:24}") long executableDelayHours) {
        this.repository = repository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.auditTrailService = auditTrailService;
        this.externalPaymentsMath = externalPaymentsMath;
        this.ownerDestinationAddress = ownerDestinationAddress != null ? ownerDestinationAddress.trim() : "";
        this.executableDelay = Duration.ofHours(Math.max(0L, executableDelayHours));
    }

    @Transactional
    public SiphonRequest requestPayout(
            String idempotencyKey,
            String requestedBy,
            BigDecimal requestedAmount) {
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        Optional<SiphonRequest> existing = repository.findByIdempotencyKey(normalizedIdempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        repository.findFirstByStatusInOrderByRequestedAtAsc(ACTIVE_STATUSES).ifPresent(active -> {
            throw new IllegalStateException("Treasury payout already has an active request: " + active.getId());
        });

        LocalDateTime now = LocalDateTime.now();
        String destinationAddress = requireValidDestination(ownerDestinationAddress);
        BigDecimal pendingProfit = normalizeBtc(ledgerEntryRepository.calculatePlatformProfitPendingUpTo(now));
        if (pendingProfit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("No platform fees are available for payout.");
        }

        BigDecimal amount = requestedAmount != null ? normalizeBtc(requestedAmount) : pendingProfit;
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payout amount must be positive.");
        }
        if (amount.compareTo(pendingProfit) != 0) {
            throw new IllegalArgumentException(
                    "Treasury payouts must settle the exact pending revenue snapshot.");
        }

        SiphonRequest request = new SiphonRequest();
        request.setAmount(amount);
        request.setDestinationAddress(destinationAddress);
        request.setIdempotencyKey(normalizedIdempotencyKey);
        request.setRequestedAt(now);
        request.setRequestedBy(normalizeActor(requestedBy));
        request.setRevenueCutoffAt(now);
        request.setExecutableAfter(now.plus(executableDelay));
        request.setNextAttemptAt(now.plus(executableDelay));
        request.setStatus(SiphonRequestStatus.REQUESTED);
        request.setRetryable(false);
        request.setAttempts(0);
        request.setUpdatedAt(now);

        try {
            SiphonRequest saved = repository.saveAndFlush(request);
            auditTrailService.recordBestEffort(
                    "TREASURY_PAYOUT_REQUESTED",
                    "TREASURY_PAYOUT",
                    saved.getId().toString(),
                    null,
                    LogSanitizer.fingerprint(normalizedIdempotencyKey),
                    auditPayload(saved, Map.of("pendingProfitBtc", pendingProfit.toPlainString())));
            return saved;
        } catch (DataIntegrityViolationException duplicate) {
            return repository.findByIdempotencyKey(normalizedIdempotencyKey)
                    .orElseThrow(() -> duplicate);
        }
    }

    @Transactional
    public SiphonRequest approveAndQueue(
            UUID requestId,
            String approvedBy,
            String approvalReference) {
        SiphonRequest request = repository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Treasury payout request not found."));

        if (request.getStatus() == SiphonRequestStatus.SETTLED) {
            return request;
        }
        if (request.getStatus() == SiphonRequestStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled treasury payout cannot be approved.");
        }
        if (request.getStatus() == SiphonRequestStatus.EXECUTING) {
            throw new IllegalStateException("Treasury payout is already executing.");
        }
        if (request.getStatus() == SiphonRequestStatus.QUEUED) {
            return request;
        }
        if (request.getStatus() == SiphonRequestStatus.FAILED && request.isRetryable()) {
            request.setStatus(SiphonRequestStatus.QUEUED);
            request.setNextAttemptAt(LocalDateTime.now());
            request.setLastError(null);
            request.setUpdatedAt(LocalDateTime.now());
            auditTrailService.recordBestEffort(
                    "TREASURY_PAYOUT_REQUEUED",
                    "TREASURY_PAYOUT",
                    request.getId().toString(),
                    null,
                    request.getProviderReference(),
                    auditPayload(request, Map.of("attempts", request.getAttempts())));
            return request;
        }
        if (request.getStatus() != SiphonRequestStatus.REQUESTED
                && request.getStatus() != SiphonRequestStatus.APPROVED) {
            throw new IllegalStateException("Treasury payout cannot be approved from status " + request.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        request.setStatus(SiphonRequestStatus.APPROVED);
        request.setApprovedBy(normalizeActor(approvedBy));
        request.setApprovedAt(now);
        request.setApprovalReference(normalizeApprovalReference(approvalReference));
        request.setUpdatedAt(now);
        auditTrailService.recordBestEffort(
                "TREASURY_PAYOUT_APPROVED",
                "TREASURY_PAYOUT",
                request.getId().toString(),
                null,
                request.getApprovalReference(),
                auditPayload(request, Map.of("approvedBy", request.getApprovedBy())));

        request.setStatus(SiphonRequestStatus.QUEUED);
        request.setQueuedAt(now);
        request.setNextAttemptAt(request.getExecutableAfter().isAfter(now) ? request.getExecutableAfter() : now);
        request.setUpdatedAt(now);
        auditTrailService.recordBestEffort(
                "TREASURY_PAYOUT_QUEUED",
                "TREASURY_PAYOUT",
                request.getId().toString(),
                null,
                LogSanitizer.fingerprint(request.getIdempotencyKey()),
                auditPayload(request, Map.of("nextAttemptAt", request.getNextAttemptAt().toString())));
        return request;
    }

    @Transactional
    public SiphonRequest cancel(UUID requestId, String cancelledBy, String reason) {
        SiphonRequest request = repository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Treasury payout request not found."));
        if (request.getStatus() == SiphonRequestStatus.SETTLED) {
            throw new IllegalStateException("Settled treasury payout cannot be cancelled.");
        }
        if (request.getStatus() == SiphonRequestStatus.EXECUTING) {
            throw new IllegalStateException("Executing treasury payout cannot be cancelled.");
        }
        if (request.getStatus() == SiphonRequestStatus.CANCELLED) {
            return request;
        }

        LocalDateTime now = LocalDateTime.now();
        request.setStatus(SiphonRequestStatus.CANCELLED);
        request.setRetryable(false);
        request.setCancelledBy(normalizeActor(cancelledBy));
        request.setCancelledAt(now);
        request.setCancelReason(trim(reason, 1000));
        request.setClaimedBy(null);
        request.setClaimedAt(null);
        request.setUpdatedAt(now);
        auditTrailService.recordBestEffort(
                "TREASURY_PAYOUT_CANCELLED",
                "TREASURY_PAYOUT",
                request.getId().toString(),
                null,
                LogSanitizer.fingerprint(request.getIdempotencyKey()),
                auditPayload(request, Map.of("cancelledBy", request.getCancelledBy())));
        return request;
    }

    @Transactional
    public List<SiphonRequest> claimDue(String workerId) {
        String normalizedWorkerId = normalizeActor(workerId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleClaimBefore = now.minus(STALE_CLAIM_AFTER);
        return repository.findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByRequestedAtAsc(
                        CLAIM_CANDIDATE_STATUSES,
                        now).stream()
                .map(candidate -> claim(candidate.getId(), normalizedWorkerId, now, staleClaimBefore))
                .flatMap(Optional::stream)
                .toList();
    }

    @Transactional(readOnly = true)
    public PayoutBacklogSnapshot backlogSnapshot() {
        return new PayoutBacklogSnapshot(
                repository.countByStatusIn(CLAIM_CANDIDATE_STATUSES),
                repository.maxAttemptsByStatusIn(CLAIM_CANDIDATE_STATUSES));
    }

    private Optional<SiphonRequest> claim(
            UUID requestId,
            String workerId,
            LocalDateTime now,
            LocalDateTime staleClaimBefore) {
        int updated = repository.claimDue(
                requestId,
                DUE_STATUSES,
                SiphonRequestStatus.EXECUTING,
                SiphonRequestStatus.FAILED,
                now,
                staleClaimBefore,
                workerId);
        if (updated == 0) {
            return Optional.empty();
        }
        return repository.findById(requestId);
    }

    public BigDecimal normalizeBtc(BigDecimal value) {
        BigDecimal safeValue = value != null ? value : BigDecimal.ZERO;
        return safeValue.setScale(8, RoundingMode.HALF_UP);
    }

    public long btcToSats(BigDecimal value) {
        return normalizeBtc(value).multiply(SATS_PER_BTC).longValueExact();
    }

    private String requireValidDestination(String destination) {
        String value = destination == null ? "" : destination.trim();
        if (value.isBlank() || !externalPaymentsMath.isValidBitcoinAddress(value)) {
            throw new IllegalStateException("Treasury payout destination address is not valid.");
        }
        return value;
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        String value = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required for treasury payout.");
        }
        if (value.length() > 160) {
            throw new IllegalArgumentException("idempotencyKey must have at most 160 characters.");
        }
        return value;
    }

    private String normalizeActor(String actor) {
        String value = actor == null ? "" : actor.trim();
        if (value.isBlank()) {
            return "treasury-admin";
        }
        return value.toLowerCase(Locale.ROOT).substring(0, Math.min(128, value.length()));
    }

    private String normalizeApprovalReference(String approvalReference) {
        String value = approvalReference == null ? "" : approvalReference.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("approval reference is required for treasury payout.");
        }
        return trim(value, 255);
    }

    private Map<String, Object> auditPayload(SiphonRequest request, Map<String, ?> extra) {
        java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("status", request.getStatus().name());
        payload.put("amountBtc", request.getAmount().toPlainString());
        payload.put("destinationRef", LogSanitizer.fingerprint(request.getDestinationAddress()));
        payload.put("idempotencyRef", LogSanitizer.fingerprint(request.getIdempotencyKey()));
        payload.put("revenueCutoffAt", request.getRevenueCutoffAt().toString());
        payload.putAll(extra);
        return payload;
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    public record PayoutBacklogSnapshot(long backlog, int maxAttempts) {
    }
}
