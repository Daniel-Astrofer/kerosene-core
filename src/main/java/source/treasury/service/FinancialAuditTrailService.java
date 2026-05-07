package source.treasury.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import source.common.infra.logging.LogSanitizer;
import source.treasury.entity.FinancialAuditEventEntity;
import source.treasury.repository.FinancialAuditEventRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@Service
public class FinancialAuditTrailService {

    private static final String GENESIS_HASH = "0".repeat(64);

    private final FinancialAuditEventRepository repository;
    private final ObjectMapper objectMapper;

    public FinancialAuditTrailService(FinancialAuditEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FinancialAuditEventEntity record(
            String eventType,
            String aggregateType,
            String aggregateId,
            Long userId,
            String reference,
            Map<String, ?> payload) {
        String payloadJson = toJson(payload);
        String payloadHash = sha256(payloadJson);
        String previousHash = repository.findTopByOrderBySequenceNumberDesc()
                .map(FinancialAuditEventEntity::getEventHash)
                .orElse(GENESIS_HASH);

        FinancialAuditEventEntity event = new FinancialAuditEventEntity();
        event.setEventType(trim(eventType, 96));
        event.setAggregateType(trim(aggregateType, 64));
        event.setAggregateId(trim(aggregateId, 128));
        event.setUserId(userId);
        event.setReference(trim(reference, 255));
        // Privacy invariant: the backend keeps the sequential proof, not the
        // readable financial payload. User-readable history belongs on mobile.
        event.setPayloadJson(null);
        event.setPayloadHash(payloadHash);
        event.setPreviousHash(previousHash);
        event.setEventHash(sha256(previousHash + "|" + payloadHash + "|" + eventType + "|" + aggregateId));
        return repository.save(event);
    }

    public void recordBestEffort(
            String eventType,
            String aggregateType,
            String aggregateId,
            Long userId,
            String reference,
            Map<String, ?> payload) {
        try {
            record(eventType, aggregateType, aggregateId, userId, reference, payload);
        } catch (RuntimeException exception) {
            org.slf4j.LoggerFactory.getLogger(FinancialAuditTrailService.class)
                    .error("[FinancialAuditTrail] Failed to persist audit event type={} aggregate={} ref={}: {}",
                            eventType,
                            aggregateId,
                            LogSanitizer.fingerprint(reference),
                            exception.getMessage());
        }
    }

    private String toJson(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload != null ? payload : Map.of());
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash audit event", exception);
        }
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
