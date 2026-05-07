package source.bitcoinaccounts.service;

import org.springframework.stereotype.Service;
import source.bitcoinaccounts.model.AuditEventEntity;
import source.bitcoinaccounts.repository.AuditEventRepository;

import java.util.Map;

@Service
public class BitcoinAccountAuditService {

    private final AuditEventRepository repository;

    public BitcoinAccountAuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    public void recordSystem(String action, String entityType, String entityId, Map<String, ?> metadata) {
        record("SYSTEM", null, action, entityType, entityId, metadata);
    }

    public void recordUser(Long userId, String action, String entityType, String entityId, Map<String, ?> metadata) {
        record("USER", userId != null ? userId.toString() : null, action, entityType, entityId, metadata);
    }

    private void record(String actorType, String actorId, String action, String entityType, String entityId,
            Map<String, ?> metadata) {
        AuditEventEntity event = new AuditEventEntity();
        event.setActorType(actorType);
        event.setActorId(actorId);
        event.setAction(action);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setMetadataRedacted(toRedactedJson(metadata));
        repository.save(event);
    }

    private String toRedactedJson(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> entry : metadata.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(safe(entry.getKey())).append('"').append(':')
                    .append('"').append(safe(String.valueOf(entry.getValue()))).append('"');
        }
        json.append('}');
        return json.toString();
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
