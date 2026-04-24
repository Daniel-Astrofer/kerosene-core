package source.auth.dto;

public record PasskeyDeviceDTO(
        String credentialId,
        String deviceName,
        String relyingPartyId,
        String originHost,
        String compatibilityStatus,
        boolean compatibleWithCurrentLogin) {
}
