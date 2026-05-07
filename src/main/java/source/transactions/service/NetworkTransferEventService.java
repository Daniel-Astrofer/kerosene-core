package source.transactions.service;

import org.springframework.stereotype.Service;
import source.common.infra.logging.LogSanitizer;
import source.common.observability.FinancialOperationsMetrics;
import source.transactions.model.ExternalTransferEntity;
import source.transactions.model.NetworkTransferEventEntity;
import source.transactions.repository.NetworkTransferEventRepository;
import source.treasury.service.FinancialAuditTrailService;

import java.util.Map;

@Service
public class NetworkTransferEventService {

    private final NetworkTransferEventRepository eventRepository;
    private final FinancialAuditTrailService auditTrailService;
    private final FinancialOperationsMetrics metrics;

    public NetworkTransferEventService(
            NetworkTransferEventRepository eventRepository,
            FinancialAuditTrailService auditTrailService,
            FinancialOperationsMetrics metrics) {
        this.eventRepository = eventRepository;
        this.auditTrailService = auditTrailService;
        this.metrics = metrics;
    }

    public void info(ExternalTransferEntity transfer, String eventType, String reference, String payload) {
        persist(transfer, eventType, "INFO", reference, payload);
    }

    public void warn(ExternalTransferEntity transfer, String eventType, String reference, String payload) {
        persist(transfer, eventType, "WARN", reference, payload);
    }

    public void warn(Long userId, String eventType, String reference, String payload) {
        persist(userId, eventType, "WARN", reference, payload);
    }

    public void error(ExternalTransferEntity transfer, String eventType, String reference, String payload) {
        persist(transfer, eventType, "ERROR", reference, payload);
    }

    public void info(Long userId, String eventType, String reference, String payload) {
        persist(userId, eventType, "INFO", reference, payload);
    }

    private void persist(Long userId, String eventType, String severity, String reference, String payload) {
        NetworkTransferEventEntity event = new NetworkTransferEventEntity();
        event.setUserId(userId);
        event.setEventType(eventType);
        event.setSeverity(severity);
        event.setReference(reference);
        event.setPayload(LogSanitizer.sanitizeFinancialPayload(payload));
        NetworkTransferEventEntity saved = eventRepository.save(event);
        auditTrailService.recordBestEffort(
                "NETWORK_TRANSFER_EVENT",
                "NETWORK_TRANSFER",
                null,
                userId,
                reference,
                Map.of(
                        "eventType", eventType,
                        "severity", severity,
                        "reference", reference != null ? reference : "",
                        "eventId", saved.getId().toString()));
        metrics.increment("network_transfer_event", severity, eventType);
    }

    private void persist(
            ExternalTransferEntity transfer,
            String eventType,
            String severity,
            String reference,
            String payload) {
        NetworkTransferEventEntity event = new NetworkTransferEventEntity();
        if (transfer != null) {
            event.setTransferId(transfer.getId());
            event.setUserId(transfer.getUserId());
        }
        event.setEventType(eventType);
        event.setSeverity(severity);
        event.setReference(reference);
        event.setPayload(LogSanitizer.sanitizeFinancialPayload(payload));
        NetworkTransferEventEntity saved = eventRepository.save(event);
        auditTrailService.recordBestEffort(
                "NETWORK_TRANSFER_EVENT",
                "NETWORK_TRANSFER",
                transfer != null && transfer.getId() != null ? transfer.getId().toString() : null,
                event.getUserId(),
                reference,
                Map.of(
                        "eventType", eventType,
                        "severity", severity,
                        "reference", reference != null ? reference : "",
                        "eventId", saved.getId().toString()));
        metrics.increment("network_transfer_event", severity, eventType);
    }
}
