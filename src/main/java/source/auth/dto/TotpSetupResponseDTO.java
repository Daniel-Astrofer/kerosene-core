package source.auth.dto;

public record TotpSetupResponseDTO(
        String otpUri,
        String secret) {
}
