package com.kerosene.common.financial;

import java.util.Map;

/**
 * KFE -> Core relay: publish a user-scoped STOMP frame on the auth/server broker.
 *
 * <p>{@code destination} must be a relative user queue (e.g. {@code /queue/balance}),
 * not a {@code /user/...} path - {@code convertAndSendToUser} adds the user prefix.
 */
public record StompUserPublishRequest(
        Long userId,
        String destination,
        Map<String, Object> payload) {

    public StompUserPublishRequest {
        if (userId == null) throw new IllegalArgumentException("userId required");
        if (destination == null || destination.isBlank()) throw new IllegalArgumentException("destination required");
        if (payload == null) throw new IllegalArgumentException("payload required");
    }
}
