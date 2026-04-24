package source.transactions.service;

import org.springframework.stereotype.Service;
import source.transactions.model.ExternalTransferEntity;
import source.transactions.model.NetworkTransferEventEntity;
import source.transactions.repository.NetworkTransferEventRepository;

@Service
public class NetworkTransferEventService {

    private final NetworkTransferEventRepository eventRepository;

    public NetworkTransferEventService(NetworkTransferEventRepository eventRepository) {
        this.eventRepository = eventRepository;
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
        event.setPayload(payload);
        eventRepository.save(event);
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
        event.setPayload(payload);
        eventRepository.save(event);
    }
}
