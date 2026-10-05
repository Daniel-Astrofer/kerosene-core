package com.kerosene.auth.dto;

import java.time.LocalDateTime;

/**
 * Public inventory row for one passkey, including relying-party compatibility and optional device metadata.
 * @param credentialRef public credential reference, never the private key
 * @param deviceName user-facing device label
 * @param brand optional manufacturer metadata
 * @param model optional device model
 * @param serialNumber optional potentially identifying serial metadata
 * @param deviceInstallId installation identity used by device management
 * @param platform operating system or client platform
 * @param browser browser identifier when available
 * @param firstAccessAt first observed successful use
 * @param lastAccessAt most recent successful use
 * @param status credential lifecycle state
 * @param relyingPartyId relying-party identifier bound to the credential
 * @param originHost origin host associated with the credential
 * @param compatibilityStatus textual result of current client compatibility evaluation
 * @param compatibleWithCurrentLogin whether this credential can be used in the active login context
 */
public record PasskeyDeviceDTO(
        String credentialRef,
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
        String originHost,
        String compatibilityStatus,
        boolean compatibleWithCurrentLogin) {
}
