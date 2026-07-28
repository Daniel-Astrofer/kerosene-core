package com.kerosene.common.financial;

/**
 * Policy hash for vault mesh spend policy verification.
 */
public record PolicyHash(String value) {
    public PolicyHash {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("policy hash required");
        if (value.length() < 32) throw new IllegalArgumentException("policy hash too short: " + value.length());
    }

    @Override
    public String toString() { return value; }
}
