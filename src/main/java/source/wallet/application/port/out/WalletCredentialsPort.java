package source.wallet.application.port.out;

public interface WalletCredentialsPort {

    void validateBip39Passphrase(char[] passphrase);

    String hashPassphrase(char[] passphrase);

    boolean matches(char[] rawPassphrase, String hashedPassphrase);

    String generateTotpSecret();

    String buildWalletTotpUri(String walletName, String totpSecret);
}
