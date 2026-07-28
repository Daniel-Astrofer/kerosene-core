package com.kerosene.common.financial;

/**
 * Reason code for vault mesh settlement status.
 */
public record ReasonCode(String value) {
    public ReasonCode {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("reason code required");
    }

    @Override
    public String toString() { return value; }
}
