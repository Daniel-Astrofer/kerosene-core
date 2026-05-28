package source.notification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import source.notification.dto.DeviceTokenRegisterRequest;
import source.notification.dto.DeviceTokenResponse;
import source.notification.model.entity.NotificationEntity;
import source.notification.service.NotificationDeviceTokenService;
import source.notification.service.NotificationService;

import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService service;
    private final NotificationDeviceTokenService deviceTokenService;

    public NotificationController(
            NotificationService service,
            NotificationDeviceTokenService deviceTokenService) {
        this.service = service;
        this.deviceTokenService = deviceTokenService;
    }

    @GetMapping
    public ResponseEntity<List<NotificationEntity>> getNotifications(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(service.getUserNotifications(userId));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        service.markAsRead(userId, id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/register-token")
    public ResponseEntity<DeviceTokenResponse> registerToken(
            @AuthenticationPrincipal Long userId,
            @RequestBody DeviceTokenRegisterRequest request) {
        return ResponseEntity.ok(DeviceTokenResponse.from(deviceTokenService.register(userId, request)));
    }

    @GetMapping("/device-tokens")
    public ResponseEntity<List<DeviceTokenResponse>> activeDeviceTokens(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(deviceTokenService.activeTokens(userId).stream()
                .map(DeviceTokenResponse::from)
                .toList());
    }

    @DeleteMapping("/device-tokens/{id}")
    public ResponseEntity<Void> revokeToken(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        deviceTokenService.revoke(userId, id);
        return ResponseEntity.noContent().build();
    }
}
