package source.auth.application.service.cripto.hasher;

import source.auth.application.service.cripto.contracts.Hasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.nio.CharBuffer;
import java.nio.ByteBuffer;
import java.util.Arrays;

@Component("SHAHasher")
public class SHA256 implements Hasher {

    private final String hardwareKey;

    public SHA256(@Value("${api.secret.pepper.secret}") String hardwareKey) {
        this.hardwareKey = hardwareKey;
    }

    public String hash(char[] input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(hardwareKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);

            ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(input));
            byte[] inputBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(inputBytes);

            byte[] hash = mac.doFinal(inputBytes);

            Arrays.fill(inputBytes, (byte) 0);
            Arrays.fill(byteBuffer.array(), (byte) 0);

            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error generating HMAC-SHA256", e);
        }
    }

    @Override
    public Boolean verify(char[] passphrase, String hash) {
        String pass = hash(passphrase);
        return pass.equals(hash);
    }

}
