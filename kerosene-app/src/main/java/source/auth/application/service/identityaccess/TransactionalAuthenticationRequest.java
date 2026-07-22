package source.auth.application.service.identityaccess;

import source.auth.model.entity.UserDataBase;

public record TransactionalAuthenticationRequest(
        UserDataBase user,
        Long authenticatedUserId,
        Long resourceOwnerUserId,
        String totpSecret,
        String totpCode,
        String passkeyAssertionJson,
        String confirmationPassphrase,
        TransactionalAuthenticationScope scope) {

    public TransactionalAuthenticationRequest {
        if (scope == null) {
            scope = TransactionalAuthenticationScope.LEDGER_TRANSFER;
        }
    }

    public static TransactionalAuthenticationRequest kfeTransaction(
            UserDataBase sender,
            String totpCode,
            String passkeyAssertionJson,
            String confirmationPassphrase) {
        return new TransactionalAuthenticationRequest(
                sender,
                sender != null ? sender.getId() : null,
                null,
                sender != null ? sender.getTOTPSecret() : null,
                totpCode,
                passkeyAssertionJson,
                confirmationPassphrase,
                TransactionalAuthenticationScope.LEDGER_TRANSFER);
    }

    public static TransactionalAuthenticationRequest kfeCustodialTransfer(
            UserDataBase sender,
            String passkeyAssertionJson) {
        return new TransactionalAuthenticationRequest(
                sender,
                sender != null ? sender.getId() : null,
                sender != null ? sender.getId() : null,
                null,
                null,
                passkeyAssertionJson,
                null,
                TransactionalAuthenticationScope.KFE_CUSTODIAL_TRANSFER);
    }

    public static TransactionalAuthenticationRequest kfeColdWalletPsbt(
            UserDataBase sender,
            String totpCode) {
        return new TransactionalAuthenticationRequest(
                sender,
                sender != null ? sender.getId() : null,
                sender != null ? sender.getId() : null,
                sender != null ? sender.getTOTPSecret() : null,
                totpCode,
                null,
                null,
                TransactionalAuthenticationScope.KFE_COLD_WALLET_PSBT);
    }

    public static TransactionalAuthenticationRequest walletOutbound(
            Long authenticatedUserId,
            Long walletOwnerUserId,
            String walletTotpSecret,
            String totpCode,
            String passkeyAssertionJson,
            String confirmationPassphrase) {
        return new TransactionalAuthenticationRequest(
                null,
                authenticatedUserId,
                walletOwnerUserId,
                walletTotpSecret,
                totpCode,
                passkeyAssertionJson,
                confirmationPassphrase,
                TransactionalAuthenticationScope.WALLET_OUTBOUND);
    }

    public static TransactionalAuthenticationRequest accountSecurityChange(
            Long authenticatedUserId,
            String totpCode,
            String passkeyAssertionJson,
            String confirmationPassphrase) {
        return new TransactionalAuthenticationRequest(
                null,
                authenticatedUserId,
                authenticatedUserId,
                null,
                totpCode,
                passkeyAssertionJson,
                confirmationPassphrase,
                TransactionalAuthenticationScope.ACCOUNT_SECURITY_CHANGE);
    }
}
