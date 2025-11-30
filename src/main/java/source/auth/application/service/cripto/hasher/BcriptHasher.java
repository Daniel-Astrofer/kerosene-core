package source.auth.application.service.cripto.hasher;


import source.auth.application.service.cripto.contracts.Hasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component("BcryptHasher")
public class BcriptHasher implements Hasher {
    private SHA256 sha256;

    public BcriptHasher(SHA256 sha256) {
        this.sha256 = sha256;
    }

    @Override
    public String hash(String passphrase) {
        String pass = sha256.hash(passphrase);
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        return passwordEncoder.encode(pass);
    }

    @Override
    public Boolean verify(String passphrase, String hash) {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        return passwordEncoder.matches(passphrase, hash);

    }

}
