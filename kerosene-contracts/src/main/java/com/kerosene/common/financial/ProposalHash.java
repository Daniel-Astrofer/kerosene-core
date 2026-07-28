package com.kerosene.common.financial;

/**
 * Proposal hash (64 hex chars) for vault mesh governance proposals.
 */
public record ProposalHash(String value) {
    private static final int HEX_LENGTH = 64;

    public ProposalHash {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("proposal hash required");
        if (value.length() != HEX_LENGTH) throw new IllegalArgumentException("proposal hash must be " + HEX_LENGTH + " hex chars, got: " + value.length());
        if (!value.matches("[0-9a-fA-F]+")) throw new IllegalArgumentException("proposal hash must be hex");
        value = value.toLowerCase();
    }

    @Override
    public String toString() { return value; }
}
