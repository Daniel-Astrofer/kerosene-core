package source.kfe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import source.kfe.model.KfeAuditLogEntity;
import source.kfe.model.KfeTransactionStatus;
import source.kfe.repository.KfeAuditLogRepository;

import java.util.Map;
import java.util.UUID;

@Service
public class KfeAuditLogService {

    private static final String GENESIS_HASH = "0".repeat(64);

    private final KfeAuditLogRepository repository;
    private final KfeHashService hashService;
    private final ObjectMapper objectMapper;

    public KfeAuditLogService(
            KfeAuditLogRepository repository,
            KfeHashService hashService,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.hashService = hashService;
        this.objectMapper = objectMapper;
    }

    public KfeAuditLogEntity record(
            String eventType,
            UUID transactionId,
            UUID walletId,
            KfeTransactionStatus fromStatus,
            KfeTransactionStatus toStatus,
            Map<String, ?> redactedPayload) {
        String payloadHash = hashService.sha256(toJson(redactedPayload));
        String previousHash = repository.findTopByOrderBySequenceNumberDesc()
                .map(KfeAuditLogEntity::getEventHash)
                .orElse(GENESIS_HASH);

        KfeAuditLogEntity event = new KfeAuditLogEntity();
        event.setEventType(trim(eventType, 96));
        event.setTransactionId(transactionId);
        event.setWalletId(walletId);
        event.setFromStatus(fromStatus != null ? fromStatus.name() : null);
        event.setToStatus(toStatus != null ? toStatus.name() : null);
        event.setPayloadHash(payloadHash);
        event.setPreviousHash(previousHash);
        event.setEventHash(hashService.sha256(previousHash + "|" + payloadHash + "|" + eventType
                + "|" + transactionId + "|" + walletId + "|" + toStatus));
        return repository.save(event);
    }

    private String toJson(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload != null ? payload : Map.of());
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String trim(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
