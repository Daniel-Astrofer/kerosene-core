package com.kerosene.auth.application.infra.persistence.jpa;

import java.time.LocalDateTime;

/**
 * Lightweight JPA projection for listing a user's enrolled passkeys without loading verification secrets.
 *
 * @param credentialId opaque WebAuthn credential identifier
 * @param deviceName user-assigned display name
 * @param brand reported hardware/vendor brand
 * @param model reported device model
 * @param serialNumber reported serial identifier, subject to application privacy handling
 * @param deviceInstallId installation identity used to group device credentials
 * @param platform operating platform associated with the credential
 * @param browser browser/user-agent family associated with enrollment
 * @param firstAccessAt enrollment/first-access time
 * @param lastAccessAt most recent access time
 * @param status credential lifecycle state
 * @param relyingPartyId WebAuthn relying party identifier
 * @param originHost host component of the validated WebAuthn origin
 */
public record PasskeyInventoryProjection(
        byte[] credentialId,
        String deviceName,
        String brand,
        String model,
        String serialNumber,
        String deviceInstallId,
        String platform,
        String browser,
        LocalDateTime firstAccessAt,
        LocalDateTime lastAccessAt,
        String status,
        String relyingPartyId,
        String originHost) {
}
