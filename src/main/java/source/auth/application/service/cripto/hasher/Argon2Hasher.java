package source.auth.application.service.cripto.hasher;

import source.auth.application.service.cripto.contracts.Hasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

@Component("Argon2Hasher")
public class Argon2Hasher implements Hasher {

    private final Argon2PasswordEncoder encoder;
    private final String pepper;

    public Argon2Hasher(@Value("${api.secret.pepper.secret}") String pepper) {
        if (pepper == null || pepper.isBlank()) {
            throw new IllegalStateException("[Security] api.secret.pepper.secret is not configured.");
        }
        this.pepper = pepper;

        // Iterations=3, Memory=65536 (64MB), Parallelism=4, Length=32, SaltLength=16
        this.encoder = new Argon2PasswordEncoder(16, 32, 4, 65536, 3);
    }

    @Override
    public String hash(char[] passphrase) {
        char[] pepperChars = this.pepper.toCharArray();
        char[] passChars = passphrase;
        char[] combined = new char[passChars.length + pepperChars.length];

        System.arraycopy(passChars, 0, combined, 0, passChars.length);
        System.arraycopy(pepperChars, 0, combined, passChars.length, pepperChars.length);

        try {
            java.nio.CharBuffer buffer = java.nio.CharBuffer.wrap(combined);
            return encoder.encode(buffer);
        } finally {
            java.util.Arrays.fill(combined, '\0');
            java.util.Arrays.fill(pepperChars, '\0');
            java.util.Arrays.fill(passChars, '\0');
        }
    }

    @Override
    public Boolean verify(char[] passphrase, String hash) {
        char[] pepperChars = this.pepper.toCharArray();
        char[] passChars = passphrase;
        char[] combined = new char[passChars.length + pepperChars.length];

        System.arraycopy(passChars, 0, combined, 0, passChars.length);
        System.arraycopy(pepperChars, 0, combined, passChars.length, pepperChars.length);

        try {
            java.nio.CharBuffer buffer = java.nio.CharBuffer.wrap(combined);
            return encoder.matches(buffer, hash);
        } finally {
            java.util.Arrays.fill(combined, '\0');
            java.util.Arrays.fill(pepperChars, '\0');
            java.util.Arrays.fill(passChars, '\0');
        }
    }
}
