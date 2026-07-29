package com.kerosene.auth.dto;

import java.util.List;
import java.util.Map;

/**
 * Structured guidance when transactional (or login) step-up requires a device credential.
 *
 * <p>Legacy clients read {@link #challenge()} only. Release N clients should prefer
 * {@link #acceptedFactors()}, {@link #challenges()}, and {@link #preferredFactor()}.
 */
public record PasskeyActionRequiredDTO(
        String action,
        String reason,
        String challenge,
        boolean totpFallbackAvailable,
        boolean linkNewPasskeyAllowed,
        String linkPasskeyPath,
        String guidance,
        PasskeyInventoryDTO passkeys,
        List<String> acceptedFactors,
        Map<String, DeviceCredentialChallengeDTO> challenges,
        String preferredFactor) {

    /** Backward-compatible constructor used by older call sites and tests. */
    public PasskeyActionRequiredDTO(
            String action,
            String reason,
            String challenge,
            boolean totpFallbackAvailable,
            boolean linkNewPasskeyAllowed,
            String linkPasskeyPath,
            String guidance,
            PasskeyInventoryDTO passkeys) {
        this(
                action,
                reason,
                challenge,
                totpFallbackAvailable,
                linkNewPasskeyAllowed,
                linkPasskeyPath,
                guidance,
                passkeys,
                null,
                null,
                null);
    }
}
