package source.auth.model.contracts;

public interface UserDB extends User {
    String getTOTPSecret();

    void setTOTPSecret(String totpSecret);

    default boolean hasTotpEnabled() {
        String secret = getTOTPSecret();
        return secret != null && !secret.isBlank();
    }

}
