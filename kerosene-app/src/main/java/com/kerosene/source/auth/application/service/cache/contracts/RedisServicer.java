package source.auth.application.service.cache.contracts;

import source.auth.dto.UserDTO;

public interface RedisServicer {

    void createTempUser(UserDTO dto);

    UserDTO getFromRedis(UserDTO dto);

    void deleteFromRedis(UserDTO dto);

    // Generic methods
    Long increment(String key);

    void expire(String key, long timeoutSeconds);

    String getValue(String key);

    String getAndDeleteValue(String key);

    void setValue(String key, String value, long timeoutSeconds);

    void deleteValue(String key);

    default void revokeJwtSession(String sessionId, long timeoutSeconds) {
        setValue(jwtRevokedSessionKey(sessionId), "1", timeoutSeconds);
    }

    default boolean isJwtSessionRevoked(String sessionId) {
        return getValue(jwtRevokedSessionKey(sessionId)) != null;
    }

    static String jwtRevokedSessionKey(String sessionId) {
        return "auth:jwt:revoked-session:" + sessionId;
    }
}
