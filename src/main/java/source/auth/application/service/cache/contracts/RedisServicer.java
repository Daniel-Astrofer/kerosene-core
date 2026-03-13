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

    void setValue(String key, String value, long timeoutSeconds);

    void deleteValue(String key);
}
