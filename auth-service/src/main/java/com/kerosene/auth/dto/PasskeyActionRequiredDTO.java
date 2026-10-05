package com.kerosene.auth.dto;

import java.util.List;
import java.util.Map;

/**
 * Structured guidance when transactional (or login) step-up requires a device credential.
 * @param action client action to perform next
 * @param reason explanation code for the required step-up
 * @param challenge legacy single challenge field for older clients
 * @param totpFallbackAvailable whether the operation accepts TOTP as an alternate factor
 * @param linkNewPasskeyAllowed whether the client may register another passkey
 * @param linkPasskeyPath route used to start passkey enrollment
 * @param guidance user-facing instruction for completing the step-up
 * @param passkeys current public passkey inventory
 * @param acceptedFactors factor kinds accepted by the current operation
 * @param challenges challenge descriptors keyed by factor kind
 * @param preferredFactor factor clients should try first
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

    /**
     * Backward-compatible constructor for older call sites that only expose a single challenge.
     * @param action next client action
     * @param reason step-up reason
     * @param challenge legacy challenge value
     * @param totpFallbackAvailable whether TOTP can be used
     * @param linkNewPasskeyAllowed whether enrollment is permitted
     * @param linkPasskeyPath enrollment route
     * @param guidance user-facing guidance
     * @param passkeys current passkey inventory
     */
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
