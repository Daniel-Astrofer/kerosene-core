package source.auth.application.service.authentication.contracts;

public interface SignupVerifier {

    void checkUsernameNotNull(String username);

    void checkPassphraseNotNull(char[] passphrase);

    void checkUsernameFormat(String username);

    void checkUsernameLength(String username);

    void checkPassphraseLength(char[] passphrase);

    void checkPassphraseBip39(char[] passphrase);

    void checkUsernameExists(String username);

    boolean verify(String username, char[] passphrase);

}
