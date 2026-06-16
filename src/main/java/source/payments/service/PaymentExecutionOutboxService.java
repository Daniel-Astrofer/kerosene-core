package source.payments.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.common.infra.logging.LogSanitizer;
import source.payments.model.PaymentExecutionOutboxEntity;
import source.payments.model.PaymentIntentEntity;
import source.payments.repository.PaymentExecutionOutboxRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentExecutionOutboxService {

    private static final List<String> DUE_STATUSES = List.of("PENDING", "FAILED_RETRYABLE");
    private static final List<String> CLAIM_CANDIDATE_STATUSES = List.of("PENDING", "FAILED_RETRYABLE", "PROCESSING");
    private static final Duration STALE_CLAIM_AFTER = Duration.ofMinutes(10);

    private final PaymentExecutionOutboxRepository repository;

    public PaymentExecutionOutboxService(PaymentExecutionOutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PaymentExecutionOutboxEntity enqueue(PaymentIntentEntity intent, String idempotencyKey) {
        if (intent == null || intent.getId() == null) {
            throw new IllegalArgumentException("paymentIntentId is required for payment execution outbox");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required for payment execution outbox");
        }

        PaymentExecutionOutboxEntity entity = new PaymentExecutionOutboxEntity();
        entity.setPaymentIntentId(intent.getId());
        entity.setRail(intent.getRail().name());
        entity.setIdempotencyKey(idempotencyKey);
        entity.setStatus("PENDING");
        entity.setNextAttemptAt(Instant.now());
        entity.setPayloadJson(payload(intent));

        try {
            return repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException duplicate) {
            return repository.findByPaymentIntentId(intent.getId())
                    .or(() -> repository.findByIdempotencyKey(idempotencyKey))
                    .orElseThrow(() -> duplicate);
        }
    }



    @Transactional
    public List<PaymentExecutionOutboxEntity> claimDue(String workerId) {
        String normalizedWorkerId = normalizeWorkerId(workerId);
        Instant now = Instant.now();
        Instant staleClaimBefore = now.minus(STALE_CLAIM_AFTER);
        return repository.findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        CLAIM_CANDIDATE_STATUSES,
                        now).stream()
                .map(candidate -> claim(candidate.getId(), normalizedWorkerId, now, staleClaimBefore))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<PaymentExecutionOutboxEntity> claim(
            UUID outboxId,
            String workerId,
            Instant now,
            Instant staleClaimBefore) {
        int updated = repository.claimDue(outboxId, DUE_STATUSES, now, staleClaimBefore, workerId);
        if (updated == 0) {
            return Optional.empty();
        }
        return repository.findById(outboxId);
    }

    private String normalizeWorkerId(String workerId) {
        String value = workerId == null ? "" : workerId.trim();
        if (value.isBlank()) {
            return "payment-execution-worker";
        }
        return value.toLowerCase(Locale.ROOT).substring(0, Math.min(128, value.length()));
    }

    private String payload(PaymentIntentEntity intent) {
        return "{"
                + "\"paymentIntentId\":\"" + intent.getId() + "\","
                + "\"rail\":\"" + intent.getRail().name() + "\","
                + "\"destinationRef\":\"" + LogSanitizer.fingerprint(intent.getExternalDestination()) + "\","
                + "\"totalDebitSats\":" + intent.getTotalDebitSats() + ","
                + "\"receiverAmountSats\":" + intent.getReceiverAmountSats()
                + "}";
    }
}
