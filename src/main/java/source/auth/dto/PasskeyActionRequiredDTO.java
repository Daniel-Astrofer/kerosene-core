package source.auth.dto;

public record PasskeyActionRequiredDTO(
        String action,
        String reason,
        String challenge,
        boolean totpFallbackAvailable,
        boolean linkNewPasskeyAllowed,
        String linkPasskeyPath,
        String guidance,
        PasskeyInventoryDTO passkeys) {
}
