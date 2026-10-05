package com.kerosene.auth.application.service.identityaccess;

/** Sensitive operation classes whose requirements may vary with account security mode. */
public enum TransactionalAuthenticationScope {
    /** General ledger transfer authorization; allows presented TOTP even when not mode-required. */
    LEDGER_TRANSFER(false),
    /** Wallet outbound authorization; requires a platform co-signature for advanced account modes. */
    WALLET_OUTBOUND(true),
    /** Account security changes use factor policy without platform co-signing. */
    ACCOUNT_SECURITY_CHANGE(false),
    /** KFE custodial transfer requires either WebAuthn or device-key step-up. */
    KFE_CUSTODIAL_TRANSFER(false),
    /** Cold-wallet PSBT approval requires TOTP explicitly. */
    KFE_COLD_WALLET_PSBT(false);

    /** Whether this operation can request a platform signature after factor authorization. */
    private final boolean platformSignatureRequired;

    /** Creates a scope with its platform co-sign capability. */
    /** @param platformSignatureRequired whether platform signing may be required */
    TransactionalAuthenticationScope(boolean platformSignatureRequired) {
        this.platformSignatureRequired = platformSignatureRequired;
    }

    /** Returns whether this scope participates in platform co-signing. */
    /** @return true when the caller may require platform signature material */
    public boolean platformSignatureRequired() {
        return platformSignatureRequired;
    }
}
