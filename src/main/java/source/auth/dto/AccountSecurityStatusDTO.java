package source.auth.dto;

public record AccountSecurityStatusDTO(
        boolean passwordConfigured,
        boolean passkeyRegistered,
        boolean totpEnabled,
        int backupCodesRemaining,
        boolean unprotected,
        String warningMessage,
        boolean accountActivated,
        boolean inboundEnabled) {
}
