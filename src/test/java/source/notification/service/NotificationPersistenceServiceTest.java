package source.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import source.notification.model.NotificationKind;
import source.notification.model.NotificationSeverity;
import source.notification.model.UserNotificationPayload;
import source.notification.model.entity.NotificationEntity;
import source.notification.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class NotificationPersistenceServiceTest {

    @Mock
    private NotificationRepository repository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private NotificationPersistenceService service;

    @Test
    void shouldPersistNotificationAndPublishEvent() {
        UserNotificationPayload payload = UserNotificationPayload.create(
                NotificationKind.SYSTEM_INFO,
                NotificationSeverity.INFO,
                "Saldo atualizado",
                "O crédito foi confirmado.",
                "/wallet",
                "PAYMENT_LINK",
                "pay_123",
                Map.of("channel", "ledger"));

        when(repository.save(any(NotificationEntity.class))).thenAnswer(invocation -> {
            NotificationEntity entity = invocation.getArgument(0);
            entity.setId(42L);
            return entity;
        });

        NotificationEntity saved = service.persist(7L, payload);

        assertNotNull(saved);
        assertEquals(42L, saved.getId());
        assertEquals(7L, saved.getUserId());
        assertEquals(payload.kind(), saved.getKind());
        verify(eventPublisher).publishEvent(argThat((Object event) -> {
            if (!(event instanceof NotificationPersistedEvent persistedEvent)) {
                return false;
            }
            return persistedEvent.userId().equals(7L)
                    && persistedEvent.payload().get("id").equals(42L)
                    && persistedEvent.payload().get("title").equals("Saldo atualizado")
                    && persistedEvent.payload().get("entityId").equals("pay_123");
        }));
    }
}
