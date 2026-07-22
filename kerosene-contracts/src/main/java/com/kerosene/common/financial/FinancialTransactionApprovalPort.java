package source.common.financial;

public interface FinancialTransactionApprovalPort {

    void approveLocalFactor(Long userId, String deviceRef, String factor);

    void approveCustodyTransfer(Long userId, String assertion);

    void approveWalletOutbound(
            Long actorUserId,
            Long ownerUserId,
            String factorA,
            String factorB,
            String factorC);

    void approveColdWalletPsbt(Long userId, String factor);
}
