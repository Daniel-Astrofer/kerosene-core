package source.notification.service;

import org.junit.jupiter.api.Test;
import source.common.financial.FinancialNotificationAuditPort;
import source.notification.dto.DeviceTokenRegisterRequest;
import source.notification.model.entity.NotificationDeviceTokenEntity;
import source.notification.repository.NotificationDeviceTokenRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDeviceTokenServiceTest {

    private final NotificationDeviceTokenRepository repository = mock(NotificationDeviceTokenRepository.class);
    private final FinancialNotificationAuditPort auditPort = mock(FinancialNotificationAuditPort.class);
    private final NotificationDeviceTokenService service = new NotificationDeviceTokenService(repository, auditPort);

    @Test
    void registersTokenWithoutPersistingRawToken() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());
        when(repository.save(any(NotificationDeviceTokenEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationDeviceTokenEntity entity = service.register(42L, new DeviceTokenRegisterRequest(
                "android",
                "token-value-that-is-long-enough",
                "device-1",
                "1.2.3"));

        assertEquals(42L, entity.getUserId());
        assertEquals("ANDROID", entity.getPlatform());
        assertEquals(64, entity.getTokenHash().length());
        assertNotEquals("token-value-that-is-long-enough", entity.getTokenHash());
        assertTrue(entity.getTokenRef().startsWith("sha256:"));
        assertTrue(entity.getDeviceRef().startsWith("sha256:"));
        assertNull(entity.getRevokedAt());
        verify(auditPort).recordDeviceTokenEvent(
                eq("NOTIFICATION_DEVICE_TOKEN_REGISTERED"),
                anyMap());
    }

    @Test
    void duplicateTokenUpdatesExistingRow() {
        NotificationDeviceTokenEntity existing = new NotificationDeviceTokenEntity();
        existing.setUserId(7L);
        existing.setPlatform("IOS");
        existing.setTokenHash("old");
        existing.setTokenRef("old-ref");
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(existing));
        when(repository.save(any(NotificationDeviceTokenEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationDeviceTokenEntity entity = service.register(42L, new DeviceTokenRegisterRequest(
                "web",
                "token-value-that-is-long-enough",
                null,
                null));

        assertEquals(42L, entity.getUserId());
        assertEquals("WEB", entity.getPlatform());
        assertNull(entity.getRevokedAt());
    }

    @Test
    void rejectsInvalidToken() {
        assertThrows(IllegalArgumentException.class, () -> service.register(42L, new DeviceTokenRegisterRequest(
                "android",
                "short",
                null,
                null)));
    }
}
