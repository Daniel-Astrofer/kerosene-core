package source.auth.application.service.cache.contracts;


import source.auth.dto.UserDTO;


public interface RedisServicer {

    void createTempUser(UserDTO dto);

    UserDTO getFromRedis(UserDTO dto);

    void deleteFromRedis(UserDTO dto);
}
