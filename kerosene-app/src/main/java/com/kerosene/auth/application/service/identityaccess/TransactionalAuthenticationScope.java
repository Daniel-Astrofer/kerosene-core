package source.auth.application.service.identityaccess;

public enum TransactionalAuthenticationScope {
    LEDGER_TRANSFER(false),
    WALLET_OUTBOUND(true),
    ACCOUNT_SECURITY_CHANGE(false),
    KFE_CUSTODIAL_TRANSFER(false),
    KFE_COLD_WALLET_PSBT(false);

    private final boolean platformSignatureRequired;

    TransactionalAuthenticationScope(boolean platformSignatureRequired) {
        this.platformSignatureRequired = platformSignatureRequired;
    }

    public boolean platformSignatureRequired() {
        return platformSignatureRequired;
    }
}
