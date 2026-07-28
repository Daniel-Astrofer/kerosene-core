package com.kerosene.common.financial;

import java.util.Set;

/**
 * Validated settlement rail identifier.
 */
public record Rail(String value) {
    private static final Set<String> VALID = Set.of("BITCOIN_ONCHAIN", "LIGHTNING", "INTERNAL");

    public Rail {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("rail value required");
        String upper = value.toUpperCase();
        if (!VALID.contains(upper)) throw new IllegalArgumentException("unknown rail: " + value);
        value = upper;
    }

    @Override
    public String toString() { return value; }
}
