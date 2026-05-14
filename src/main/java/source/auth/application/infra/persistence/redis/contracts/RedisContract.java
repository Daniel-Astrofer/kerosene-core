package source.auth.application.infra.persistence.redis.contracts;

import source.auth.dto.UserDTO;
import source.auth.dto.SignupState;
import source.auth.dto.EmergencyRecoveryState;

public interface RedisContract {

    void save(String key, UserDTO dto, long expirationInMinutes);

    UserDTO find(String key, UserDTO dto);

    void delete(String key, UserDTO dto);

    // SignupState specific methods
    void saveSignupState(String sessionId, SignupState state, long expirationInMinutes);

    SignupState findSignupState(String sessionId);

    /**
     * Atomically fetches and deletes the SignupState in a single Redis GETDEL call.
     */
    SignupState getdelSignupState(String sessionId);

    void deleteSignupState(String sessionId);

    // EmergencyRecoveryState specific methods
    void saveEmergencyRecoveryState(String sessionId, EmergencyRecoveryState state, long expirationInMinutes);

    EmergencyRecoveryState findEmergencyRecoveryState(String sessionId);

    EmergencyRecoveryState getdelEmergencyRecoveryState(String sessionId);

    void deleteEmergencyRecoveryState(String sessionId);

    // Generic methods for security features
    Long increment(String key);

    void expire(String key, long timeoutSeconds);

    String getValue(String key);

    String getAndDeleteValue(String key);

    void setValue(String key, String value, long timeoutSeconds);

    void deleteValue(String key);
}
