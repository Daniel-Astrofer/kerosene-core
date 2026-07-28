package com.kerosene.common.financial;

/**
 * Non-negative satoshi amount for financial operations.
 */
public record SatoshiAmount(long sats) {
    public SatoshiAmount {
        if (sats < 0) throw new IllegalArgumentException("sats must be >= 0, got: " + sats);
    }

    @Override
    public String toString() { return String.valueOf(sats); }
}
