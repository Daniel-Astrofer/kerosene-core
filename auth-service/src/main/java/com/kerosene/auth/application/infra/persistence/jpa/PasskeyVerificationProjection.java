package com.kerosene.auth.application.infra.persistence.jpa;

/**
 * Minimal joined projection needed to verify a WebAuthn assertion and enforce account state.
 *
 * @param credentialId credential identifier from authenticator data
 * @param publicKeyCose COSE-encoded public key used to verify the assertion signature
 * @param signatureCount last accepted authenticator counter for replay protection
 * @param status current credential lifecycle state
 * @param relyingPartyId configured WebAuthn relying party identifier
 * @param originHost validated origin host bound to the credential
 * @param userId owning user identifier
 * @param username canonical username for authentication context
 * @param userActive whether the owning account is active
 */
public record PasskeyVerificationProjection(
        byte[] credentialId,
        byte[] publicKeyCose,
        long signatureCount,
        String status,
        String relyingPartyId,
        String originHost,
        Long userId,
        String username,
        Boolean userActive) {
}
