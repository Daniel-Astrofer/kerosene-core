package com.kerosene.common.financial;

/**
 * Lightning payment hash (64 hex chars).
 */
public record PaymentHash(String value) {
    private static final int HEX_LENGTH = 64;

    public PaymentHash {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("payment hash required");
        if (value.length() != HEX_LENGTH) throw new IllegalArgumentException("payment hash must be " + HEX_LENGTH + " hex chars, got: " + value.length());
        if (!value.matches("[0-9a-fA-F]+")) throw new IllegalArgumentException("payment hash must be hex");
        value = value.toLowerCase();
    }

    @Override
    public String toString() { return value; }
}
