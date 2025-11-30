package source.auth.application.service.cripto.hasher;

import source.auth.application.service.cripto.contracts.Hasher;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Component("SHAHasher")
public class SHA256 implements Hasher {

    /**
     * Encrypter
     *
     * @param input receive the passphrase and encrypt
     * @return return the hash of the passphrase
     * @throws RuntimeException when the algorithmic is invalid
     */
    public String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Boolean verify(String passphrase, String hash) {
        String pass = hash(passphrase);
        return pass.equals(hash);
    }


}
