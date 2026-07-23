package com.kerosene.common.financial;

public record FinancialCustodyTransferApprovalRequest(
        Long userId,
        String assertion) {
}
