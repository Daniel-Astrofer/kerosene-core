package source.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import source.content.dto.HomeSurfaceResponseDTO;
import source.content.dto.HomeUiEventDTO;

import java.time.Instant;

@Service
public class HomeUiPushService {

    public static final String QUEUE_DESTINATION = "/queue/home-ui";

    private static final Logger log = LoggerFactory.getLogger(HomeUiPushService.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public HomeUiPushService(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    public void pushSnapshot(Long userId, HomeSurfaceResponseDTO surface) {
        if (userId == null || surface == null) {
            return;
        }
        JsonNode payload = objectMapper.valueToTree(surface);
        push(userId, "HOME_UI_SNAPSHOT", surface.version(), payload);
    }

    public void pushPatch(Long userId, JsonNode patch, String version) {
        if (userId == null || patch == null) {
            return;
        }
        String ver = version == null || version.isBlank() ? Instant.now().toString() : version;
        push(userId, "HOME_UI_PATCH", ver, patch);
    }

    public void pushGreeting(Long userId, JsonNode greetingPayload, String version) {
        if (userId == null || greetingPayload == null) {
            return;
        }
        String ver = version == null || version.isBlank() ? Instant.now().toString() : version;
        push(userId, "HOME_UI_GREETING", ver, greetingPayload);
    }

    public void pushFeedDelta(Long userId, JsonNode delta, String version) {
        if (userId == null || delta == null) {
            return;
        }
        String ver = version == null || version.isBlank() ? Instant.now().toString() : version;
        push(userId, "HOME_UI_FEED_DELTA", ver, delta);
    }

    private void push(Long userId, String type, String version, JsonNode payload) {
        try {
            HomeUiEventDTO event = new HomeUiEventDTO(type, version, payload);
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    QUEUE_DESTINATION,
                    event);
            log.info("Pushed home-ui {} to user {}", type, userId);
        } catch (Exception ex) {
            log.warn("Failed to push home-ui {} to user {}: {}", type, userId, ex.getMessage());
        }
    }
}
