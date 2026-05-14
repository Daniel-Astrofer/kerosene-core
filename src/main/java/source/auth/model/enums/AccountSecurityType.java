package source.auth.model.enums;

/**
 * Defines the account security mode chosen by the user at signup.
 *
 * <ul>
 * <li>{@link #STANDARD} — password + TOTP; no platform co-signer secret.</li>
 * <li>{@link #SHAMIR} — Shamir Secret Sharing; the platform holds one
 * AES-256-GCM
 * encrypted share and acts as one of the required co-signers.</li>
 * <li>{@link #MULTISIG_2FA} — Platform holds an encrypted signing key that is
 * required alongside the user's own authentication for every operation.</li>
 * </ul>
 *
 * For {@code SHAMIR} and {@code MULTISIG_2FA} the encrypted share is stored in
 * {@code users_credentials.platform_cosigner_secret} and is
 * <strong>never returned via any public API</strong>.
 */
public enum AccountSecurityType {

    /** Standard password + TOTP. Platform stores no secret. */
    STANDARD,

    /**
     * Shamir Secret Sharing mode.
     * Platform holds 1 of N shares, encrypted with the platform master key.
     */
    SHAMIR,

    /**
     * Multisig 2-FA mode.
     * Platform holds an encrypted co-signing key. Every sensitive operation
     * requires both the user to authenticate AND the platform to co-sign.
     */
    MULTISIG_2FA,

    /**
     * Passkey-only mode.
     * Primary authentication is done via FIDO2/WebAuthn.
     */
    PASSKEY
}
