package source.auth.application.service.cache;

import source.auth.application.infra.persistance.redis.contracts.RedisContract;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.auth.application.service.cripto.contracts.Cryptography;
import source.auth.application.service.cripto.contracts.Hasher;
import source.auth.dto.UserDTO;
import source.security.VaultKeyProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class RedisService implements RedisServicer {

    private final Cryptography cryptography;
    private final RedisContract repository;
    private final Hasher hasher;
    private final VaultKeyProvider vaultKeyProvider;
    private static final String key = "signup:";

    public RedisService(
            RedisContract repository,
            @Qualifier("aes256") Cryptography cryptography,
            @Qualifier("Argon2Hasher") Hasher hasher,
            VaultKeyProvider vaultKeyProvider) {
        this.repository = repository;
        this.cryptography = cryptography;
        this.hasher = hasher;
        this.vaultKeyProvider = vaultKeyProvider;
        // Chave AES vive no VaultKeyProvider (RAM-only, pós-atestação TPM)
    }

    @Override
    public void createTempUser(UserDTO userDTO) {

        String normalizedPassphrase = new String(userDTO.getPassphrase()).trim().replaceAll("[\\s\\u00A0]+", " ");
        userDTO.setPassphrase(hasher.hash(normalizedPassphrase.toCharArray()).toCharArray());

        try {
            String base64 = Base64.getEncoder()
                    .encodeToString(
                            cryptography.encrypt(userDTO.getTotpSecret()
                                    .getBytes(StandardCharsets.UTF_8), vaultKeyProvider.getMasterKey()));
            userDTO.setTotpSecret(base64);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error encrypting TOTP secret");
        }
        repository.save(key, userDTO, 120);

    }

    @Override
    public UserDTO getFromRedis(UserDTO dto) {

        return repository.find(key, dto);
    }

    @Override
    public void deleteFromRedis(UserDTO dto) {
        repository.delete(key, dto);
    }

    @Override
    public Long increment(String key) {
        return repository.increment(key);
    }

    @Override
    public void expire(String key, long timeoutSeconds) {
        repository.expire(key, timeoutSeconds);
    }

    @Override
    public String getValue(String key) {
        return repository.getValue(key);
    }

    @Override
    public void setValue(String key, String value, long timeoutSeconds) {
        repository.setValue(key, value, timeoutSeconds);
    }

    @Override
    public void deleteValue(String key) {
        repository.deleteValue(key);
    }

}
