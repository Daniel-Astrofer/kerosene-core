package source.notification.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import source.notification.service.NotificationService;
import source.common.dto.ApiResponse;

import java.util.Map;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<String>> sendNotification(@RequestBody Map<String, String> payload) {
        String userIdStr = payload.get("userId");
        String title = payload.get("title");
        String body = payload.get("body");

        if (userIdStr == null || title == null || body == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("userId, title, and body are all required fields to send a notification.",
                            "ERR_NOTIF_MISSING_FIELDS"));
        }

        try {
            Long userId = Long.parseLong(userIdStr);
            notificationService.notifyUser(userId, title, body);
            return ResponseEntity
                    .ok(ApiResponse.success("Push notification has been successfully dispatched to the target user."));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid userId format.", "ERR_NOTIF_INVALID_USERID"));
        }
    }
}
