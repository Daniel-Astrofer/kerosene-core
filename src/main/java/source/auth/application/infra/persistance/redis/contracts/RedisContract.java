package source.auth.application.infra.persistance.redis.contracts;

import source.auth.dto.UserDTO;

public interface RedisContract {

    void save(String key, UserDTO dto, long expirationInMinutes);

    UserDTO find(String key, UserDTO dto);

    void delete(String key, UserDTO dto);


}
