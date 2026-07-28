package com.kerosene.common.financial;

import java.util.Arrays;

/**
 * Cryptographic nonce with length validation.
 */
public record Nonce(byte[] value) {
    private static final int MIN_LENGTH = 12;
    private static final int MAX_LENGTH = 32;

    public Nonce {
        if (value == null || value.length == 0) throw new IllegalArgumentException("nonce required");
        if (value.length < MIN_LENGTH) throw new IllegalArgumentException("nonce too short: " + value.length + " (min " + MIN_LENGTH + ")");
        if (value.length > MAX_LENGTH) throw new IllegalArgumentException("nonce too long: " + value.length + " (max " + MAX_LENGTH + ")");
        value = value.clone();
    }

    public byte[] value() { return value.clone(); }

    @Override
    public String toString() { return "Nonce[len=" + value.length + "]"; }

    @Override
    public boolean equals(Object o) {
        return o instanceof Nonce other && Arrays.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }
}
