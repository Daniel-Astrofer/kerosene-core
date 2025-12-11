package source.auth.application.service.cache;


import jakarta.transaction.Transactional;
import source.auth.application.infra.persistance.redis.contracts.RedisContract;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.auth.application.service.cripto.contracts.Cryptography;
import source.auth.application.service.cripto.contracts.Hasher;
import source.auth.dto.UserDTO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;


@Service
public class RedisService implements RedisServicer {

    private final Cryptography cryptography;
    private final RedisContract repository;
    private final Hasher hasher;
    private static final String key = "signup:";
    private String keybase;
    private SecretKey secretKey;


    public RedisService(
            RedisContract repository,
            @Qualifier("aes256") Cryptography cryptography,
            @Qualifier("SHAHasher") Hasher hasher,
            @Value("${api.secret.aes.secret}") String keybase
    ) {
        this.repository = repository;
        this.cryptography = cryptography;
        this.hasher = hasher;
        this.keybase = keybase;

        byte[] decodedKey = Base64.getDecoder().decode(keybase);
        this.secretKey = new SecretKeySpec(decodedKey, "AES");
    }

    @Override
    public void createTempUser(UserDTO userDTO) {

        userDTO.setPassphrase(
                hasher.hash(userDTO.getPassphrase()));

        try {
            String base64 = Base64.getEncoder()
                    .encodeToString(
                            cryptography.encrypt(userDTO.getTotpSecret()
                                    .getBytes(StandardCharsets.UTF_8), secretKey));
            userDTO.setTotpSecret(base64);

        } catch (Exception e) {
            throw new RuntimeException("Error encrypting TOTP secret");
        }
        repository.save(key, userDTO, 120);
        /**/
    }

    @Override
    public UserDTO getFromRedis(UserDTO dto) {

        return repository.find(key, dto);
    }

    @Override
    public void deleteFromRedis(UserDTO dto) {
        repository.delete(key, dto);
    }

}
