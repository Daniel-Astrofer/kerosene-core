package source.auth.dto.contracts;

public interface UserDTOContract {

    String getUsername();

    String getPassphrase();

    String getTotpSecret();

    String getTotpCode();

    void setUsername(String username);

    void setPassphrase(String passphrase);

    void setTotpSecret(String totpSecret);

    void setTotpCode(String totpCode);


}
