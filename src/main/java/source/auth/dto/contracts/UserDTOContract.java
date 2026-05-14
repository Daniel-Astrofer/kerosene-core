package source.auth.dto.contracts;

public interface UserDTOContract {

    String getUsername();

    char[] getPassphrase();

    String getTotpSecret();

    String getTotpCode();

    void setUsername(String username);

    void setPassphrase(char[] passphrase);

    void setTotpSecret(String totpSecret);

    void setTotpCode(String totpCode);

    String getPreAuthToken();

    void setPreAuthToken(String preAuthToken);

}
