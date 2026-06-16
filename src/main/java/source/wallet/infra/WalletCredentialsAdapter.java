package source.wallet.infra;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import source.auth.AuthConstants;
import source.auth.application.service.cripto.contracts.Hasher;
import source.wallet.application.port.out.WalletCredentialsPort;
import source.wallet.domain.InternalWalletMnemonicPolicy;

@Component
public class WalletCredentialsAdapter implements WalletCredentialsPort {

    private final InternalWalletMnemonicPolicy mnemonicPolicy;
    private final Hasher hasher;
    private final source.auth.application.service.validation.totp.contracts.TOTPKeyGenerate totpKeyGenerate;

    public WalletCredentialsAdapter(
            @Qualifier("Argon2Hasher") Hasher hasher,
            source.auth.application.service.validation.totp.contracts.TOTPKeyGenerate totpKeyGenerate) {
        this.mnemonicPolicy = new InternalWalletMnemonicPolicy();
        this.hasher = hasher;
        this.totpKeyGenerate = totpKeyGenerate;
    }

    @Override
    public void validateBip39Passphrase(char[] passphrase) {
        mnemonicPolicy.validate(passphrase);
    }

    @Override
    public String hashPassphrase(char[] passphrase) {
        return hasher.hash(passphrase);
    }

    @Override
    public boolean matches(char[] rawPassphrase, String hashedPassphrase) {
        return hasher.verify(rawPassphrase, hashedPassphrase);
    }

    @Override
    public String generateTotpSecret() {
        return totpKeyGenerate.keyGenerator();
    }

    @Override
    public String buildWalletTotpUri(String walletName, String totpSecret) {
        return String.format(
                AuthConstants.TOTP_URI_FORMAT,
                AuthConstants.APP_NAME,
                walletName + " (Wallet)",
                totpSecret,
                AuthConstants.APP_NAME);
    }
}
