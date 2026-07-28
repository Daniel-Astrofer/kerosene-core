package com.kerosene.common.financial;

/**
 * Cold wallet PSBT approval with typed device proof.
 * NEVER log or serialize raw factor fields.
 */
public record FinancialColdWalletPsbtApprovalRequest(
        Long userId,
        DeviceProof factor) {

    public FinancialColdWalletPsbtApprovalRequest {
        if (userId == null) throw new IllegalArgumentException("userId required");
        if (factor == null) throw new IllegalArgumentException("factor required");
    }

    @Override
    public String toString() {
        return "FinancialColdWalletPsbtApprovalRequest[userId=" + userId
                + ", factor=" + factor + "]";
    }
}
