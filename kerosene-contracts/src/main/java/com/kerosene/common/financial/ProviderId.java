package com.kerosene.common.financial;

/**
 * Provider identifier for external services (LND, Bitcoind, etc.).
 */
public record ProviderId(String value) {
    public ProviderId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("provider id required");
    }

    @Override
    public String toString() { return value; }
}
