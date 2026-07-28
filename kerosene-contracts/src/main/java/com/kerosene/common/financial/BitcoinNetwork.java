package com.kerosene.common.financial;

import java.util.Set;

/**
 * Bitcoin network identifier.
 */
public record BitcoinNetwork(String value) {
    private static final Set<String> VALID = Set.of("MAINNET", "TESTNET", "REGTEST", "SIGNET");

    public BitcoinNetwork {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("network value required");
        String upper = value.toUpperCase();
        if (!VALID.contains(upper)) throw new IllegalArgumentException("unknown bitcoin network: " + value);
        value = upper;
    }

    @Override
    public String toString() { return value; }
}
