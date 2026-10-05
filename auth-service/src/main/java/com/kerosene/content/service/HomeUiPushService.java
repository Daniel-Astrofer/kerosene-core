package com.kerosene.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import com.kerosene.content.dto.HomeSurfaceResponseDTO;
import com.kerosene.content.dto.HomeUiEventDTO;

import java.time.Instant;

/** Sends versioned home-surface events to an individual user's WebSocket queue. */
@Service
public class HomeUiPushService {

    /** User-specific destination consumed by connected home clients. */
    public static final String QUEUE_DESTINATION = "/queue/home-ui";

    /** Logger for best-effort event delivery failures. */
    private static final Logger log = LoggerFactory.getLogger(HomeUiPushService.class);

    /** Spring broker gateway for per-user messages. */
    private final SimpMessagingTemplate messagingTemplate;
    /** Serializer used to convert snapshots into JSON payload trees. */
    private final ObjectMapper objectMapper;

    /** Creates the push service with its broker and JSON serializer. */
    /** @param messagingTemplate broker template for user destinations @param objectMapper serializer for snapshot payloads */
    public HomeUiPushService(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    /** Converts and pushes a complete composed surface snapshot to the user. */
    /** @param userId recipient account identifier @param surface complete surface snapshot */
    public void pushSnapshot(Long userId, HomeSurfaceResponseDTO surface) {
        if (userId == null || surface == null) {
            return;
        }
        JsonNode payload = objectMapper.valueToTree(surface);
        push(userId, "HOME_UI_SNAPSHOT", surface.version(), payload);
    }

    /** Pushes a partial surface patch, assigning a timestamp version when none is supplied. */
    /** @param userId recipient account identifier @param patch partial JSON object @param version patch revision, or blank to generate one */
    public void pushPatch(Long userId, JsonNode patch, String version) {
        if (userId == null || patch == null) {
            return;
        }
        String ver = version == null || version.isBlank() ? Instant.now().toString() : version;
        push(userId, "HOME_UI_PATCH", ver, patch);
    }

    /** Pushes a greeting-only event to update the currently displayed home message. */
    /** @param userId recipient account identifier @param greetingPayload greeting JSON @param version content revision, or blank to generate one */
    public void pushGreeting(Long userId, JsonNode greetingPayload, String version) {
        if (userId == null || greetingPayload == null) {
            return;
        }
        String ver = version == null || version.isBlank() ? Instant.now().toString() : version;
        push(userId, "HOME_UI_GREETING", ver, greetingPayload);
    }

    /** Pushes an incremental feed update without resending the full surface. */
    /** @param userId recipient account identifier @param delta feed delta JSON @param version feed revision, or blank to generate one */
    public void pushFeedDelta(Long userId, JsonNode delta, String version) {
        if (userId == null || delta == null) {
            return;
        }
        String ver = version == null || version.isBlank() ? Instant.now().toString() : version;
        push(userId, "HOME_UI_FEED_DELTA", ver, delta);
    }

    /** Wraps a payload as a typed event and sends it; broker failures are logged and contained. */
    /** @param userId recipient account identifier @param type event discriminator @param version event revision @param payload event data */
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
