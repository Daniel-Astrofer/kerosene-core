package com.kerosene.common.financial;

/**
 * Local factor approval with typed device proof.
 * NEVER log or serialize raw factor fields.
 */
public record FinancialLocalFactorApprovalRequest(
        Long userId,
        String deviceRef,
        DeviceProof factor) {

    public FinancialLocalFactorApprovalRequest {
        if (userId == null) throw new IllegalArgumentException("userId required");
        if (deviceRef == null || deviceRef.isBlank()) throw new IllegalArgumentException("deviceRef required");
        if (factor == null) throw new IllegalArgumentException("factor required");
    }

    @Override
    public String toString() {
        return "FinancialLocalFactorApprovalRequest[userId=" + userId
                + ", deviceRef=" + deviceRef
                + ", factor=" + factor + "]";
    }
}
