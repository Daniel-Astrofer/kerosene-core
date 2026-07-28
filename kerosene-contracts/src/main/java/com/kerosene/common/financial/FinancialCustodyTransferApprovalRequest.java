package com.kerosene.common.financial;

/**
 * Custody transfer approval with typed passkey assertion.
 * NEVER log or serialize raw assertion fields.
 */
public record FinancialCustodyTransferApprovalRequest(
        Long userId,
        PasskeyAssertion assertion) {

    public FinancialCustodyTransferApprovalRequest {
        if (userId == null) throw new IllegalArgumentException("userId required");
        if (assertion == null) throw new IllegalArgumentException("assertion required");
    }

    @Override
    public String toString() {
        return "FinancialCustodyTransferApprovalRequest[userId=" + userId
                + ", assertion=" + assertion + "]";
    }
}
