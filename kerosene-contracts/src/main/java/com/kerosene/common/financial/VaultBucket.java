package com.kerosene.common.financial;

import java.util.Set;

/**
 * Vault mesh bucket identifier.
 */
public record VaultBucket(String value) {
    private static final Set<String> VALID = Set.of("USERS", "CHANNELS", "RESERVES");

    public VaultBucket {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("bucket value required");
        String upper = value.toUpperCase();
        if (!VALID.contains(upper)) throw new IllegalArgumentException("unknown vault bucket: " + value);
        value = upper;
    }

    @Override
    public String toString() { return value; }
}
