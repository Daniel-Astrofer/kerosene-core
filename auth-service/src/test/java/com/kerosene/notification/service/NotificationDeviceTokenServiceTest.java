package com.kerosene.notification.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import com.kerosene.auth.application.service.security.CosignerSecretService;
import com.kerosene.common.financial.FinancialNotificationAuditPort;
import com.kerosene.notification.dto.DeviceTokenRegisterRequest;
import com.kerosene.notification.model.entity.NotificationDeviceTokenEntity;
import com.kerosene.notification.repository.NotificationDeviceTokenRepository;

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
    @SuppressWarnings("unchecked")
    private final ObjectProvider<CosignerSecretService> cryptoProvider = mock(ObjectProvider.class);
    private final NotificationDeviceTokenService service =
            new NotificationDeviceTokenService(repository, auditPort, cryptoProvider);

    @Test
    void registersTokenWithoutPersistingRawToken() {
        when(cryptoProvider.getIfAvailable()).thenReturn(null);
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
        assertNull(entity.getTokenCiphertext());
        verify(auditPort).recordDeviceTokenEvent(
                eq("NOTIFICATION_DEVICE_TOKEN_REGISTERED"),
                anyMap());
    }

    @Test
    void registersTokenWithCiphertextWhenCryptoAvailable() {
        CosignerSecretService crypto = mock(CosignerSecretService.class);
        when(cryptoProvider.getIfAvailable()).thenReturn(crypto);
        when(crypto.encrypt(any())).thenReturn("cipher-blob");
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());
        when(repository.save(any(NotificationDeviceTokenEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationDeviceTokenEntity entity = service.register(42L, new DeviceTokenRegisterRequest(
                "android",
                "token-value-that-is-long-enough",
                "device-1",
                "1.2.3"));

        assertEquals("cipher-blob", entity.getTokenCiphertext());
        verify(crypto).encrypt(any());
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
