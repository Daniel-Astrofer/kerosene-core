package source.notification.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import source.notification.dto.DeviceTokenRegisterRequest;
import source.notification.dto.DeviceTokenResponse;
import source.notification.model.entity.NotificationDeviceTokenEntity;
import source.notification.service.NotificationDeviceTokenService;
import source.notification.service.NotificationService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationControllerTest {

    @Test
    void registerTokenReturnsSafeResponseWithoutRawToken() {
        NotificationService notificationService = mock(NotificationService.class);
        NotificationDeviceTokenService deviceTokenService = mock(NotificationDeviceTokenService.class);
        DeviceTokenRegisterRequest request = new DeviceTokenRegisterRequest(
                "ANDROID",
                "token-value-that-is-long-enough",
                "device",
                "1.0.0");
        NotificationDeviceTokenEntity entity = new NotificationDeviceTokenEntity();
        entity.setUserId(42L);
        entity.setPlatform("ANDROID");
        entity.setTokenHash("a".repeat(64));
        entity.setTokenRef("sha256:tokenref");
        entity.setDeviceRef("sha256:deviceref");
        entity.setAppVersion("1.0.0");
        when(deviceTokenService.register(42L, request)).thenReturn(entity);

        NotificationController controller = new NotificationController(notificationService, deviceTokenService);

        ResponseEntity<DeviceTokenResponse> response = controller.registerToken(42L, request);

        assertEquals("ANDROID", response.getBody().platform());
        assertEquals("sha256:tokenref", response.getBody().tokenRef());
        verify(deviceTokenService).register(42L, request);
    }
}
