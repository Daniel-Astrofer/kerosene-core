package com.kerosene.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import com.kerosene.notification.model.NotificationKind;
import com.kerosene.notification.model.NotificationSeverity;
import com.kerosene.notification.model.UserNotificationPayload;
import com.kerosene.notification.model.entity.NotificationEntity;
import com.kerosene.notification.repository.NotificationRepository;

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
        assertEquals("ledger", saved.getMetadata().get("channel"));
        assertEquals(
                Instant.parse(payload.createdAt()),
                saved.getCreatedAtUtc());
        verify(eventPublisher).publishEvent(argThat((Object event) -> {
            if (!(event instanceof NotificationPersistedEvent persistedEvent)) {
                return false;
            }
            Object meta = persistedEvent.payload().get("metadata");
            return persistedEvent.userId().equals(7L)
                    && persistedEvent.payload().get("id").equals(42L)
                    && persistedEvent.payload().get("title").equals("Saldo atualizado")
                    && persistedEvent.payload().get("entityId").equals("pay_123")
                    && payload.createdAt().equals(persistedEvent.payload().get("createdAt"))
                    && payload.createdAt().equals(persistedEvent.payload().get("timestamp"))
                    && meta instanceof Map<?, ?> map
                    && "ledger".equals(map.get("channel"));
        }));
    }
}
