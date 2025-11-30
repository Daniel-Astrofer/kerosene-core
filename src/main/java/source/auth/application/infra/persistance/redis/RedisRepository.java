package source.auth.application.infra.persistance.redis;

import source.auth.AuthExceptions;
import source.auth.application.infra.persistance.redis.contracts.RedisContract;
import source.auth.dto.UserDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;


@Repository
public class RedisRepository implements RedisContract {
    private final ObjectMapper mapper;
    private final StringRedisTemplate redis;


    public RedisRepository(ObjectMapper mapper, StringRedisTemplate redis) {
        this.mapper = mapper;
        this.redis = redis;
    }


    @Override
    public void save(String key, UserDTO dto, long expirationInMinutes) {

        try {
            String json = mapper.writeValueAsString(dto);
            redis.opsForValue().set(key + dto.getUsername(), json, expirationInMinutes, TimeUnit.MINUTES);

        } catch (JsonProcessingException e) {
            throw new AuthExceptions.InvalidCredentials("Incompatible username or passphrase");
        }
    }

    @Override
    public UserDTO find(String key, UserDTO dto) {
        try {
            System.out.println(dto.getUsername());
            return mapper.readValue(redis.opsForValue().get("signup:" + dto.getUsername()), source.auth.dto.UserDTO.class);
        } catch (JsonProcessingException e) {
            throw new AuthExceptions.InvalidCredentials("User not found");
        }
    }

    @Override
    public void delete(String key, UserDTO dto) {
        if (!redis.delete(key+dto.getUsername())) {
            throw new AuthExceptions.InvalidCredentials("Temporary user not found to delete");
        }
    }
}
