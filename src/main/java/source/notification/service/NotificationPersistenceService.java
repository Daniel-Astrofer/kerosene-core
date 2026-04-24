package source.notification.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.notification.model.UserNotificationPayload;
import source.notification.model.entity.NotificationEntity;
import source.notification.repository.NotificationRepository;

@Service
public class NotificationPersistenceService {

    private final NotificationRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public NotificationPersistenceService(
            NotificationRepository repository,
            ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public NotificationEntity persist(Long userId, UserNotificationPayload payload) {
        NotificationEntity entity = new NotificationEntity();
        entity.setUserId(userId);
        entity.setKind(payload.kind());
        entity.setSeverity(payload.severity());
        entity.setTitle(payload.title());
        entity.setBody(payload.body());
        entity.setDeeplink(payload.deeplink());
        entity.setEntityType(payload.entityType());
        entity.setEntityId(payload.entityId());
        entity.setRead(false);

        NotificationEntity saved = repository.save(entity);

        Map<String, Object> payloadMap = new LinkedHashMap<>(payload.toMap());
        payloadMap.put("id", saved.getId());

        eventPublisher.publishEvent(new NotificationPersistedEvent(userId, payloadMap));
        return saved;
    }
}
