package source.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import source.common.infra.logging.LogDomain;
import source.common.infra.logging.StructuredLogEvent;

import java.util.Map;
import java.util.UUID;

/**
 * Emits immutable domain audit notifications to the AUDIT log stream.
 *
 * <p>Callers must pass only sanitized metadata. This logger deliberately records
 * row hashes and identifiers, never raw request bodies, credentials, invoices,
 * macaroons, private keys, tokens or provider payloads.
 */
@Component
public class StructuredAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(StructuredAuditLogger.class);

    public void persisted(
            AuditEventType eventType,
            Long sequenceNumber,
            UUID auditId,
            UUID transactionId,
            UUID walletId,
            String fromStatus,
            String toStatus,
            String payloadHash,
            String eventHash,
            Map<String, ?> metadata) {
        StructuredLogEvent event = StructuredLogEvent.of(
                eventType.name(),
                "audit",
                "persist",
                "Audit event persisted")
                .field("audit.sequence", sequenceNumber)
                .field("audit.id", auditId)
                .field("transactionId", transactionId)
                .field("walletId", walletId)
                .field("fromStatus", fromStatus)
                .field("toStatus", toStatus)
                .field("payloadHash", payloadHash)
                .field("eventHash", eventHash);

        if (metadata != null) {
            metadata.forEach((key, value) -> event.field("metadata." + key, value));
        }

        log.info(LogDomain.AUDIT, "audit.event.persisted", event.arguments());
    }
}
