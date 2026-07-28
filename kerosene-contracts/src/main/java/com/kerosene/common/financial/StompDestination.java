package com.kerosene.common.financial;

/**
 * Allow-listed STOMP destinations.
 */
public enum StompDestination {
    QUEUE_BALANCE("/queue/balance"),
    QUEUE_TRANSACTION("/queue/transaction"),
    QUEUE_NOTIFICATION("/queue/notification");

    private final String path;
    StompDestination(String path) { this.path = path; }
    public String path() { return path; }

    public static StompDestination fromPath(String path) {
        for (StompDestination d : values()) {
            if (d.path.equals(path)) return d;
        }
        throw new IllegalArgumentException("Unknown STOMP destination: " + path);
    }
}
