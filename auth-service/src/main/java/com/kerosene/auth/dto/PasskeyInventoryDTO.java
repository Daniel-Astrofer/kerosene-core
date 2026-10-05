package com.kerosene.auth.dto;

import java.util.List;

/**
 * Summary of registered passkeys and their compatibility with the current relying party and host.
 * @param passkeyRegistered whether at least one credential exists
 * @param compatibleForCurrentLogin whether any credential is usable in the active login context
 * @param legacyCredentialsPresent whether credentials from a legacy relying party are present
 * @param currentRelyingPartyId relying-party identifier expected by the current client
 * @param currentHost host/origin currently serving the application
 * @param devices public rows for registered devices
 */
public record PasskeyInventoryDTO(
        boolean passkeyRegistered,
        boolean compatibleForCurrentLogin,
        boolean legacyCredentialsPresent,
        String currentRelyingPartyId,
        String currentHost,
        List<PasskeyDeviceDTO> devices) {
}
