package source.auth.dto;

import java.util.List;

public record PasskeyInventoryDTO(
        boolean passkeyRegistered,
        boolean compatibleForCurrentLogin,
        boolean legacyCredentialsPresent,
        String currentRelyingPartyId,
        String currentHost,
        List<PasskeyDeviceDTO> devices) {
}
