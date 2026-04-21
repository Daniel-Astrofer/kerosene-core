package source.wallet.infra;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import source.auth.AuthConstants;
import source.auth.application.service.authentication.contracts.SignupVerifier;
import source.auth.application.service.cripto.contracts.Hasher;
import source.wallet.application.port.out.WalletCredentialsPort;

@Component
public class WalletCredentialsAdapter implements WalletCredentialsPort {

    private final SignupVerifier signupVerifier;
    private final Hasher hasher;
    private final source.auth.application.service.validation.totp.contracts.TOTPKeyGenerate totpKeyGenerate;

    public WalletCredentialsAdapter(
            SignupVerifier signupVerifier,
            @Qualifier("Argon2Hasher") Hasher hasher,
            source.auth.application.service.validation.totp.contracts.TOTPKeyGenerate totpKeyGenerate) {
        this.signupVerifier = signupVerifier;
        this.hasher = hasher;
        this.totpKeyGenerate = totpKeyGenerate;
    }

    @Override
    public void validateBip39Passphrase(String passphrase) {
        signupVerifier.checkPassphraseBip39(passphrase.toCharArray());
    }

    @Override
    public String hashPassphrase(String passphrase) {
        return hasher.hash(passphrase.toCharArray());
    }

    @Override
    public boolean matches(String rawPassphrase, String hashedPassphrase) {
        return hasher.verify(rawPassphrase.toCharArray(), hashedPassphrase);
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
