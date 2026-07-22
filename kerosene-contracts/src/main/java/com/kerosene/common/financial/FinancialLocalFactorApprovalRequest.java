package source.common.financial;

public record FinancialLocalFactorApprovalRequest(
        Long userId,
        String deviceRef,
        String factor) {
}
