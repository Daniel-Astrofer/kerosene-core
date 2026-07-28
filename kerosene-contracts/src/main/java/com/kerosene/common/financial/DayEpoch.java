package com.kerosene.common.financial;

/**
 * Vault mesh day epoch identifier.
 */
public record DayEpoch(String value) {
    public DayEpoch {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("day epoch required");
    }

    @Override
    public String toString() { return value; }
}
