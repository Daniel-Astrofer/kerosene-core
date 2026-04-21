package source.auth.application.service.authentication.login;

import source.auth.dto.contracts.UserDTOContract;
import source.auth.model.entity.UserDataBase;

public class LoginValidationContext {

    private final UserDTOContract dto;
    private String normalizedUsername;
    private String rateLimitKey;
    private UserDataBase user;
    private char[] normalizedPassphrase;

    public LoginValidationContext(UserDTOContract dto) {
        this.dto = dto;
    }

    public UserDTOContract getDto() {
        return dto;
    }

    public String getNormalizedUsername() {
        return normalizedUsername;
    }

    public void setNormalizedUsername(String normalizedUsername) {
        this.normalizedUsername = normalizedUsername;
    }

    public String getRateLimitKey() {
        return rateLimitKey;
    }

    public void setRateLimitKey(String rateLimitKey) {
        this.rateLimitKey = rateLimitKey;
    }

    public UserDataBase getUser() {
        return user;
    }

    public void setUser(UserDataBase user) {
        this.user = user;
    }

    public char[] getNormalizedPassphrase() {
        return normalizedPassphrase;
    }

    public void setNormalizedPassphrase(char[] normalizedPassphrase) {
        this.normalizedPassphrase = normalizedPassphrase;
    }
}
