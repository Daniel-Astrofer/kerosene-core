package source.auth.application.infra.persistance.redis;

import source.auth.AuthExceptions;
import source.auth.application.infra.persistance.redis.contracts.RedisContract;
import source.auth.dto.UserDTO;
import source.auth.dto.SignupState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

@Repository
public class RedisRepository implements RedisContract {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RedisRepository.class);
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
            return mapper.readValue(redis.opsForValue().get("signup:" + dto.getUsername()),
                    source.auth.dto.UserDTO.class);
        } catch (JsonProcessingException e) {
            throw new AuthExceptions.InvalidCredentials("User not found");
        }
    }

    public void delete(String key, UserDTO dto) {
        if (!redis.delete(key + dto.getUsername())) {
            throw new AuthExceptions.InvalidCredentials("Temporary user not found to delete");
        }
    }

    @Override
    public void saveSignupState(String sessionId, SignupState state, long expirationInMinutes) {
        try {
            String json = mapper.writeValueAsString(state);
            redis.opsForValue().set("signup:" + sessionId, json, expirationInMinutes, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            throw new AuthExceptions.InvalidCredentials("Failed to serialize SignupState");
        }
    }

    @Override
    public SignupState findSignupState(String sessionId) {
        try {
            String json = redis.opsForValue().get("signup:" + sessionId);
            if (json == null)
                return null;
            return mapper.readValue(json, SignupState.class);
        } catch (JsonProcessingException e) {
            throw new AuthExceptions.InvalidCredentials("Failed to parse SignupState");
        }
    }

    @Override
    public void deleteSignupState(String sessionId) {
        if (!Boolean.TRUE.equals(redis.delete("signup:" + sessionId))) {
            throw new AuthExceptions.InvalidCredentials("Temporarily saved SignupState not found to delete");
        }
    }

    @Override
    public Long increment(String key) {
        return redis.opsForValue().increment(key);
    }

    @Override
    public void expire(String key, long timeoutSeconds) {
        redis.expire(key, timeoutSeconds, TimeUnit.SECONDS);
    }

    @Override
    public String getValue(String key) {
        return redis.opsForValue().get(key);
    }

    @Override
    public void setValue(String key, String value, long timeoutSeconds) {
        redis.opsForValue().set(key, value, timeoutSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void deleteValue(String key) {
        redis.delete(key);
    }
}
