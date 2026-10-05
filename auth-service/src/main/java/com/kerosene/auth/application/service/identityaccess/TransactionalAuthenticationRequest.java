package com.kerosene.auth.application.service.identityaccess;

import com.kerosene.auth.model.entity.UserDataBase;

/**
 * Immutable factor/ownership inputs for one sensitive operation authorization.
 * @param user optional already-resolved actor entity
 * @param authenticatedUserId principal identifier authenticated by the request
 * @param resourceOwnerUserId persisted owner of the protected resource, when applicable
 * @param totpSecret optional secret selected by the resource boundary
 * @param totpCode submitted time-based one-time code
 * @param passkeyAssertionJson submitted WebAuthn or device-key assertion JSON
 * @param confirmationPassphrase optional passphrase step-up
 * @param scope policy scope; null is normalized to LEDGER_TRANSFER
 */
public record TransactionalAuthenticationRequest(
        UserDataBase user,
        Long authenticatedUserId,
        Long resourceOwnerUserId,
        String totpSecret,
        String totpCode,
        String passkeyAssertionJson,
        String confirmationPassphrase,
        TransactionalAuthenticationScope scope) {

    /** Supplies a compatibility default scope for older call sites that omit it. */
    public TransactionalAuthenticationRequest {
        if (scope == null) {
            scope = TransactionalAuthenticationScope.LEDGER_TRANSFER;
        }
    }

    /** Creates a ledger transfer request using the sender's account and TOTP secret. */
    /** @param sender sender account */
    /** @param totpCode submitted TOTP code */
    /** @param passkeyAssertionJson optional passkey/device-key assertion */
    /** @param confirmationPassphrase optional passphrase confirmation */
    /** @return request scoped to LEDGER_TRANSFER */
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

    /** Creates a custodial transfer request where the sender owns the protected resource. */
    /** @param sender sender and resource owner */
    /** @param passkeyAssertionJson required WebAuthn or device-key step-up assertion */
    /** @return request scoped to KFE_CUSTODIAL_TRANSFER */
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

    /** Creates a cold-wallet PSBT approval request using sender TOTP. */
    /** @param sender sender account */
    /** @param totpCode required TOTP code */
    /** @return request scoped to KFE_COLD_WALLET_PSBT */
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

    /** Creates an outbound wallet request with separate authenticated actor and resource owner identities. */
    /** @param authenticatedUserId principal account ID */
    /** @param walletOwnerUserId resource owner account ID */
    /** @param walletTotpSecret TOTP secret selected by the wallet owner boundary */
    /** @param totpCode submitted TOTP code */
    /** @param passkeyAssertionJson optional passkey/device-key assertion */
    /** @param confirmationPassphrase optional passphrase confirmation */
    /** @return request scoped to WALLET_OUTBOUND */
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

    /** Creates a request to authorize changes to the authenticated account's security settings. */
    /** @param authenticatedUserId principal and resource owner account ID */
    /** @param totpCode optional TOTP code */
    /** @param passkeyAssertionJson optional passkey/device-key assertion */
    /** @param confirmationPassphrase optional passphrase confirmation */
    /** @return request scoped to ACCOUNT_SECURITY_CHANGE */
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
