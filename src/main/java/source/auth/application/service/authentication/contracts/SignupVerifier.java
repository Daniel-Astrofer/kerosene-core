package source.auth.application.service.authentication.contracts;


public interface SignupVerifier {

    void checkUsernameNotNull(String username);

    void checkPassphraseNotNull(String passphrase);

    void checkUsernameFormat(String username);

    void checkUsernameLength(String username);

    void checkPassphraseLength(String passphrase);

    void checkPassphraseBip39(String passphrase);

    void checkUsernameExists(String username);

    boolean verify(String username, String passphrase);


}
