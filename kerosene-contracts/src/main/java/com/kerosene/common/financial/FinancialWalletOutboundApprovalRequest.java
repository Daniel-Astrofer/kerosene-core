package com.kerosene.common.financial;

/**
 * Multi-factor outbound approval with typed secret wrappers.
 * NEVER log or serialize raw factor fields — use {@link #toString()}.
 */
public record FinancialWalletOutboundApprovalRequest(
        Long actorUserId,
        Long ownerUserId,
        PasskeyAssertion passkeyAssertion,
        RecoveryApproval recoveryApproval,
        DeviceProof deviceProof) {

    public FinancialWalletOutboundApprovalRequest {
        if (actorUserId == null) throw new IllegalArgumentException("actorUserId required");
        if (ownerUserId == null) throw new IllegalArgumentException("ownerUserId required");
        if (passkeyAssertion == null) throw new IllegalArgumentException("passkeyAssertion required");
        if (recoveryApproval == null) throw new IllegalArgumentException("recoveryApproval required");
        if (deviceProof == null) throw new IllegalArgumentException("deviceProof required");
    }

    @Override
    public String toString() {
        return "FinancialWalletOutboundApprovalRequest[actorUserId=" + actorUserId
                + ", ownerUserId=" + ownerUserId
                + ", passkeyAssertion=" + passkeyAssertion
                + ", recoveryApproval=" + recoveryApproval
                + ", deviceProof=" + deviceProof + "]";
    }
}
