package com.kerosene.common.financial;

import java.util.Set;

/**
 * V2: destination is constrained to allowlisted queues only.
 */
public record StompUserPublishRequestV2(
    String userId,                       // non-blank
    StompDestination destination,        // enum, not arbitrary string
    StompPayload payload                 // typed, not Map<String,Object>
) {
    public static final Set<String> ALLOWED_DESTINATIONS = Set.of(
        "/queue/balance",
        "/queue/transaction",
        "/queue/notification"
    );

    public StompUserPublishRequestV2 {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId required");
        if (destination == null) throw new IllegalArgumentException("destination required");
        if (payload == null) throw new IllegalArgumentException("payload required");
    }
}
