package source.auth.application.infra.persistance.redis.contracts;

import source.auth.dto.UserDTO;
import source.auth.dto.SignupState;

public interface RedisContract {

    void save(String key, UserDTO dto, long expirationInMinutes);

    UserDTO find(String key, UserDTO dto);

    void delete(String key, UserDTO dto);

    // SignupState specific methods
    void saveSignupState(String sessionId, SignupState state, long expirationInMinutes);

    SignupState findSignupState(String sessionId);

    void deleteSignupState(String sessionId);

    // Generic methods for security features
    Long increment(String key);

    void expire(String key, long timeoutSeconds);

    String getValue(String key);

    void setValue(String key, String value, long timeoutSeconds);

    void deleteValue(String key);
}
