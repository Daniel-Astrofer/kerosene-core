package source.auth.dto.contracts;

public interface UserDTOContract {

    String getUsername();

    char[] getPassphrase();

    default char[] getPassword() {
        return getPassphrase();
    }

    String getTotpSecret();

    String getTotpCode();

    String getSessionId();

    void setUsername(String username);

    void setPassphrase(char[] passphrase);

    default void setPassword(char[] password) {
        setPassphrase(password);
    }

    void setTotpSecret(String totpSecret);

    void setTotpCode(String totpCode);

    void setSessionId(String sessionId);

    String getPreAuthToken();

    void setPreAuthToken(String preAuthToken);

}
