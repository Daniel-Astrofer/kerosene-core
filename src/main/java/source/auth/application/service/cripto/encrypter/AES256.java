package source.auth.application.service.cripto.encrypter;

import source.auth.application.service.cripto.contracts.Cryptography;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.util.Arrays;

@Component("aes256")
public class AES256 implements Cryptography {


    public byte[] encrypt(byte[] totpSecret, SecretKey key) throws Exception {

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] totp = totpSecret;

        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        byte[] criptoText = cipher.doFinal(totp);
        byte[] combined = new byte[iv.length + criptoText.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(criptoText, 0, combined, iv.length, criptoText.length);
        return combined;


    }

    public byte[] decrypt(byte[] hash, SecretKey key) throws Exception {

        byte[] iv = Arrays.copyOfRange(hash, 0, 12);
        byte[] cipherText = Arrays.copyOfRange(hash, 12, hash.length);


        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);


        return cipher.doFinal(cipherText);
    }


}
