package com.kerosene.gateway.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Allowlisted fan-out from internal HTTP (KFE standalone) onto in-process STOMP sessions.
 */
@Service
public class StompUserRelayService {

    /** Logger for successfully dispatched internal user events. */
    private static final Logger log = LoggerFactory.getLogger(StompUserRelayService.class);

    /** Destinations permitted for internal KFE-to-core publication. */
    static final Set<String> ALLOWED_DESTINATIONS = Set.of(
            "/queue/balance",
            "/queue/transactions",
            "/queue/kfe-dashboard");

    /** In-process broker template used to address a user's active STOMP sessions. */
    private final SimpMessagingTemplate messagingTemplate;

    /** Creates the relay with the configured STOMP broker gateway. */
    /** @param messagingTemplate broker template for user-directed messages */
    public StompUserRelayService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /** Validates user, allowlisted destination, and non-empty payload before broker delivery. */
    /** @param userId recipient account identifier @param destination requested queue @param payload message fields @throws IllegalArgumentException if any required field or destination is invalid */
    public void publishToUser(Long userId, String destination, Map<String, Object> payload) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        String normalized = normalizeDestination(destination);
        if (!ALLOWED_DESTINATIONS.contains(normalized)) {
            throw new IllegalArgumentException("destination is not allowlisted: " + destination);
        }
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("payload is required");
        }
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), normalized, payload);
        log.info("[WS-RELAY] Published to user {} {}", userId, normalized);
    }

    /** Removes optional Spring user prefixes and normalizes a destination into queue form. */
    /** @param destination client or server destination string @return normalized destination, or empty string for blank input */
    public static String normalizeDestination(String destination) {
        if (destination == null || destination.isBlank()) {
            return "";
        }
        String trimmed = destination.trim();
        // Clients sometimes pass the full user destination; strip the prefix.
        if (trimmed.startsWith("/user/queue/")) {
            return "/queue/" + trimmed.substring("/user/queue/".length());
        }
        if (trimmed.startsWith("/user/")) {
            String rest = trimmed.substring("/user/".length());
            int slash = rest.indexOf('/');
            if (slash >= 0) {
                return rest.substring(slash);
            }
        }
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        return trimmed.toLowerCase(Locale.ROOT).startsWith("/queue/")
                ? trimmed
                : trimmed;
    }
}
