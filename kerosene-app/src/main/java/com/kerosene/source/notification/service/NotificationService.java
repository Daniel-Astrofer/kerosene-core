package source.notification.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.notification.model.UserNotificationPayload;
import source.notification.model.entity.NotificationEntity;
import source.notification.repository.NotificationRepository;

@Service
public class NotificationService {

    private final NotificationPersistenceService notificationPersistenceService;
    private final NotificationRepository repository;

    public NotificationService(
            NotificationPersistenceService notificationPersistenceService,
            NotificationRepository repository) {
        this.notificationPersistenceService = notificationPersistenceService;
        this.repository = repository;
    }

    public void notifyUser(Long userId, UserNotificationPayload payload) {
        notificationPersistenceService.persist(userId, payload);
    }

    @Transactional(readOnly = true)
    public List<NotificationEntity> getUserNotifications(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        repository.findByIdAndUserId(notificationId, userId).ifPresent(notification -> {
            notification.setRead(true);
            repository.save(notification);
        });
    }
}
