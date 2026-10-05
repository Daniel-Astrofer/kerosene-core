package com.kerosene.auth.dto;

import java.time.LocalDateTime;

/**
 * Policy and runtime status of the user's application PIN, including lockout and verification data.
 * @param enabled whether PIN verification is enabled for protected operations
 * @param configured whether a PIN verifier has been stored
 * @param locked whether failed attempts currently prevent verification
 * @param failedAttempts number of failed checks in the current lockout window
 * @param remainingAttempts attempts available before lockout
 * @param maxAttempts configured attempt ceiling
 * @param minPinLength minimum accepted PIN length
 * @param maxPinLength maximum accepted PIN length
 * @param resettableWithTotp whether TOTP can authorize a PIN reset
 * @param deviceScoped whether PIN verification state is bound to a device
 * @param lockedUntil time lockout expires, or null when unlocked
 * @param lastVerifiedAt last successful PIN verification time
 * @param updatedAt last PIN configuration update time
 */
public record AppPinStatusDTO(
        boolean enabled,
        boolean configured,
        boolean locked,
        int failedAttempts,
        int remainingAttempts,
        int maxAttempts,
        int minPinLength,
        int maxPinLength,
        boolean resettableWithTotp,
        boolean deviceScoped,
        LocalDateTime lockedUntil,
        LocalDateTime lastVerifiedAt,
        LocalDateTime updatedAt) {
}
