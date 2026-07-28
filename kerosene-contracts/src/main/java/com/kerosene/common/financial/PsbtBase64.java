package com.kerosene.common.financial;

/**
 * Base64-encoded PSBT with basic format check.
 */
public record PsbtBase64(String value) {
    public PsbtBase64 {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("psbt required");
        if (!value.matches("^[A-Za-z0-9+/=]+$")) throw new IllegalArgumentException("psbt must be valid base64");
    }

    @Override
    public String toString() { return value; }
}
