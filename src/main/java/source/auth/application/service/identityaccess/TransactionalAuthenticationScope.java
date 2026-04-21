package source.auth.application.service.identityaccess;

public enum TransactionalAuthenticationScope {
    LEDGER_TRANSFER(false),
    WALLET_OUTBOUND(true);

    private final boolean platformSignatureRequired;

    TransactionalAuthenticationScope(boolean platformSignatureRequired) {
        this.platformSignatureRequired = platformSignatureRequired;
    }

    public boolean platformSignatureRequired() {
        return platformSignatureRequired;
    }
}
