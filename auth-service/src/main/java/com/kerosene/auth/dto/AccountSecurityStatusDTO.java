package com.kerosene.auth.dto;

/**
 * Compact API summary of password, passkey, TOTP, backup-code, and activation protection state.
 * @param passwordConfigured whether a password verifier is configured
 * @param passkeyRegistered whether at least one passkey is registered
 * @param totpEnabled whether TOTP is enabled
 * @param backupCodesRemaining number of unused recovery codes
 * @param unprotected whether the account is considered to lack sufficient protection
 * @param warningMessage user-facing security warning, or null when none applies
 * @param accountActivated whether inbound account activation is complete
 * @param inboundEnabled whether the account may receive inbound platform funds
 * @param passkeys detailed public passkey inventory summary
 */
public record AccountSecurityStatusDTO(
        boolean passwordConfigured,
        boolean passkeyRegistered,
        boolean totpEnabled,
        int backupCodesRemaining,
        boolean unprotected,
        String warningMessage,
        boolean accountActivated,
        boolean inboundEnabled,
        PasskeyInventoryDTO passkeys) {
}
