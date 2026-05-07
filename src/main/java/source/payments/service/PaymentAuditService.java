package source.payments.service;

import org.springframework.stereotype.Service;
import source.payments.model.PaymentAuditEventEntity;
import source.payments.repository.PaymentAuditEventRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentAuditService {

    private static final String GENESIS_HASH = "GENESIS";

    private final PaymentAuditEventRepository auditEventRepository;

    public PaymentAuditService(PaymentAuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    public PaymentAuditEventEntity record(Long actorUserId, UUID paymentIntentId, String eventType, Map<String, ?> payload) {
        String canonicalPayload = canonicalPayload(payload);
        String payloadHash = sha256(canonicalPayload);
        String previousHash = auditEventRepository.findTopByOrderByCreatedAtDesc()
                .map(PaymentAuditEventEntity::getCurrentHash)
                .orElse(GENESIS_HASH);
        String currentHash = sha256(previousHash + "|" + paymentIntentId + "|" + eventType + "|" + payloadHash);

        PaymentAuditEventEntity event = new PaymentAuditEventEntity();
        event.setActorUserId(actorUserId);
        event.setPaymentIntentId(paymentIntentId);
        event.setEventType(eventType);
        event.setPayloadHash(payloadHash);
        event.setPreviousHash(previousHash);
        event.setCurrentHash(currentHash);
        return auditEventRepository.save(event);
    }

    private String canonicalPayload(Map<String, ?> payload) {
        if (payload == null || payload.isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder();
        payload.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> builder
                        .append(entry.getKey())
                        .append('=')
                        .append(entry.getValue() != null ? entry.getValue() : "")
                        .append(';'));
        return builder.toString();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is required for payment audit.", exception);
        }
    }
}
