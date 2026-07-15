package source.auth.application.infra.persistence.jpa;

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
