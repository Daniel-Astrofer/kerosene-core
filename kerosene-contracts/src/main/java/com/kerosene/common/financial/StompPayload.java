package com.kerosene.common.financial;

/**
 * Typed STOMP payload — immutable, size-limited, schema-versioned.
 */
public record StompPayload(
    String eventId,
    int schemaVersion,
    String eventType,
    String serializedBody,     // already serialized, max 65536 bytes
    int bodyLength
) {
    public static final int MAX_PAYLOAD_BYTES = 65536;

    public StompPayload {
        if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId required");
        if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("eventType required");
        if (serializedBody == null) throw new IllegalArgumentException("serializedBody required");
        if (bodyLength < 0 || bodyLength > MAX_PAYLOAD_BYTES) throw new IllegalArgumentException("bodyLength out of range 0-" + MAX_PAYLOAD_BYTES);
    }
}
