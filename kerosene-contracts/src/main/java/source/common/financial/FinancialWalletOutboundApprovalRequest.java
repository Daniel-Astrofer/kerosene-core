package source.common.financial;

public record FinancialWalletOutboundApprovalRequest(
        Long actorUserId,
        Long ownerUserId,
        String factorA,
        String factorB,
        String factorC) {
}
