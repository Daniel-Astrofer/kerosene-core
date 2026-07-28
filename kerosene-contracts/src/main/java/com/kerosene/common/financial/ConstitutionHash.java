package com.kerosene.common.financial;

/**
 * Constitution hash for vault mesh governance.
 */
public record ConstitutionHash(String value) {
    public ConstitutionHash {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("constitution hash required");
        if (value.length() < 32) throw new IllegalArgumentException("constitution hash too short: " + value.length());
    }

    @Override
    public String toString() { return value; }
}
