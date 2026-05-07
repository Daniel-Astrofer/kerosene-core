package source.transactions.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.common.infra.logging.LogSanitizer;
import source.transactions.model.ExternalProviderOutboxEntity;
import source.transactions.repository.ExternalProviderOutboxRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class ExternalProviderOutboxService {

    private static final List<String> DUE_STATUSES = List.of("PENDING", "FAILED_RETRYABLE");
    private static final List<String> CLAIM_CANDIDATE_STATUSES = List.of("PENDING", "FAILED_RETRYABLE", "PROCESSING");
    private static final Duration STALE_CLAIM_AFTER = Duration.ofMinutes(10);

    private final ExternalProviderOutboxRepository repository;
    private final NetworkTransferEventService eventService;

    public ExternalProviderOutboxService(
            ExternalProviderOutboxRepository repository,
            NetworkTransferEventService eventService) {
        this.repository = repository;
        this.eventService = eventService;
    }

    @Transactional
    public ExternalProviderOutboxEntity enqueue(
            UUID transferId,
            String operationType,
            String idempotencyKey,
            String payloadJson) {
        if (transferId == null) {
            throw new IllegalArgumentException("transferId is required for provider outbox");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required for provider outbox");
        }

        ExternalProviderOutboxEntity entity = new ExternalProviderOutboxEntity();
        entity.setTransferId(transferId);
        entity.setOperationType(operationType);
        entity.setIdempotencyKey(idempotencyKey);
        entity.setPayloadJson(payloadJson);
        entity.setStatus("PENDING");
        entity.setNextAttemptAt(LocalDateTime.now());
        try {
            ExternalProviderOutboxEntity saved = repository.saveAndFlush(entity);
            eventService.info(
                    (Long) null,
                    "PROVIDER_OUTBOX_ENQUEUED",
                    LogSanitizer.fingerprint(idempotencyKey),
                    "transferId=" + transferId + " | operationType=" + operationType);
            return saved;
        } catch (DataIntegrityViolationException duplicate) {
            return repository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> duplicate);
        }
    }

    @Transactional
    public void markDispatched(UUID outboxId, String providerReference) {
        repository.findById(outboxId).ifPresent(entity -> {
            entity.setStatus("DISPATCHED");
            entity.setProviderReference(providerReference);
            entity.setDispatchedAt(LocalDateTime.now());
            entity.setLastError(null);
            clearClaim(entity);
            repository.save(entity);
        });
    }

    @Transactional
    public void markFailed(UUID outboxId, String errorMessage, boolean retryable) {
        repository.findById(outboxId).ifPresent(entity -> {
            entity.setAttempts(entity.getAttempts() + 1);
            entity.setStatus(retryable ? "FAILED_RETRYABLE" : "FAILED_FINAL");
            entity.setLastError(trim(errorMessage, 1000));
            entity.setNextAttemptAt(LocalDateTime.now().plusMinutes(Math.min(60, 1L << Math.min(entity.getAttempts(), 5))));
            clearClaim(entity);
            repository.save(entity);
        });
    }

    @Transactional
    public void markUnknown(UUID outboxId, String providerReference, String errorMessage) {
        repository.findById(outboxId).ifPresent(entity -> {
            entity.setAttempts(entity.getAttempts() + 1);
            entity.setStatus("UNKNOWN");
            entity.setProviderReference(providerReference);
            entity.setLastError(trim(errorMessage, 1000));
            entity.setNextAttemptAt(LocalDateTime.now());
            clearClaim(entity);
            repository.save(entity);
        });
    }

    @Transactional
    public List<ExternalProviderOutboxEntity> claimDue(String workerId) {
        String normalizedWorkerId = normalizeWorkerId(workerId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleClaimBefore = now.minus(STALE_CLAIM_AFTER);
        return repository.findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        CLAIM_CANDIDATE_STATUSES,
                        now).stream()
                .map(candidate -> claim(candidate.getId(), normalizedWorkerId, now, staleClaimBefore))
                .flatMap(Optional::stream)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExternalProviderOutboxEntity> findDueForAutomaticResolution() {
        return repository.findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                DUE_STATUSES,
                LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public Optional<ExternalProviderOutboxEntity> findLatestByTransferId(UUID transferId) {
        if (transferId == null) {
            return Optional.empty();
        }
        return repository.findTopByTransferIdOrderByCreatedAtDesc(transferId);
    }

    @Transactional(readOnly = true)
    public ProviderOutboxBacklogSnapshot backlogSnapshot() {
        ExternalProviderOutboxEntity oldest = repository.findFirstByStatusInOrderByCreatedAtAsc(CLAIM_CANDIDATE_STATUSES)
                .orElse(null);
        return new ProviderOutboxBacklogSnapshot(
                repository.countByStatusIn(CLAIM_CANDIDATE_STATUSES),
                oldest != null ? oldest.getCreatedAt() : null,
                repository.maxAttemptsByStatusIn(CLAIM_CANDIDATE_STATUSES));
    }

    private Optional<ExternalProviderOutboxEntity> claim(
            UUID outboxId,
            String workerId,
            LocalDateTime now,
            LocalDateTime staleClaimBefore) {
        int updated = repository.claimDue(outboxId, DUE_STATUSES, now, staleClaimBefore, workerId);
        if (updated == 0) {
            return Optional.empty();
        }
        return repository.findById(outboxId);
    }

    private String normalizeWorkerId(String workerId) {
        String value = workerId == null ? "" : workerId.trim();
        if (value.isBlank()) {
            return "external-provider-outbox-worker";
        }
        return value.toLowerCase(Locale.ROOT).substring(0, Math.min(128, value.length()));
    }

    private void clearClaim(ExternalProviderOutboxEntity entity) {
        entity.setClaimedBy(null);
        entity.setClaimedAt(null);
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    public record ProviderOutboxBacklogSnapshot(
            long backlog,
            LocalDateTime oldestPendingCreatedAt,
            int maxAttempts) {
    }
}
