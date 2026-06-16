package source.auth.application.infra.persistence.redis;

import source.auth.AuthExceptions;
import source.auth.application.infra.persistence.redis.contracts.RedisContract;
import source.auth.dto.UserDTO;
import source.auth.dto.SignupState;
import source.auth.dto.EmergencyRecoveryState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;
import java.util.Objects;

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
            // Serialisation failure = infrastructure error, NOT an auth failure.
            // Throwing InvalidCredentials (401) here is semantically wrong and hides bugs.
            throw new IllegalStateException(
                    "[Redis] Failed to serialise UserDTO for key '" + key + "': " + e.getMessage(), e);
        }
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RedisRepository.class);

    @Override
    public UserDTO find(String key, UserDTO dto) {
        try {
            log.debug("[RedisRepository] find() called for signup lookup");
            String json = redis.opsForValue().get(key + dto.getUsername());
            if (json == null) {
                return null;
            }
            return mapper.readValue(json, source.auth.dto.UserDTO.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "[Redis] Failed to deserialise UserDTO for user '" + dto.getUsername() + "': " + e.getMessage(), e);
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
            throw new IllegalStateException(
                    "[Redis] Failed to serialise SignupState for session '" + sessionId + "': " + e.getMessage(), e);
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
            throw new IllegalStateException(
                    "[Redis] Failed to deserialise SignupState for session '" + sessionId + "': " + e.getMessage(), e);
        }
    }

    @Override
    public SignupState getdelSignupState(String sessionId) {
        try {
            // Atomically fetch and delete (Redis GETDEL command) — eliminates TOCTOU race
            // condition
            String json = redis.execute(
                    (org.springframework.data.redis.connection.RedisConnection conn) -> {
                        byte[] rawKey = redis.getStringSerializer().serialize("signup:" + sessionId);
                        byte[] rawVal = conn.stringCommands().getDel(Objects.requireNonNull(rawKey));
                        return rawVal != null ? new String(rawVal, java.nio.charset.StandardCharsets.UTF_8) : null;
                    });
            if (json == null)
                return null;
            return mapper.readValue(json, SignupState.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("[Redis] Failed to deserialise SignupState (GETDEL) for session '"
                    + sessionId + "': " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteSignupState(String sessionId) {
        if (!redis.delete("signup:" + sessionId)) {
            throw new AuthExceptions.InvalidCredentials("Temporarily saved SignupState not found to delete");
        }
    }

    @Override
    public void saveEmergencyRecoveryState(String sessionId, EmergencyRecoveryState state, long expirationInMinutes) {
        try {
            String json = mapper.writeValueAsString(state);
            redis.opsForValue().set("recovery:" + sessionId, json, expirationInMinutes, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("[Redis] Failed to serialise EmergencyRecoveryState for session '"
                    + sessionId + "': " + e.getMessage(), e);
        }
    }

    @Override
    public EmergencyRecoveryState findEmergencyRecoveryState(String sessionId) {
        try {
            String json = redis.opsForValue().get("recovery:" + sessionId);
            if (json == null) {
                return null;
            }
            return mapper.readValue(json, EmergencyRecoveryState.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("[Redis] Failed to deserialise EmergencyRecoveryState for session '"
                    + sessionId + "': " + e.getMessage(), e);
        }
    }

    @Override
    public EmergencyRecoveryState getdelEmergencyRecoveryState(String sessionId) {
        try {
            String json = redis.execute(
                    (org.springframework.data.redis.connection.RedisConnection conn) -> {
                        byte[] rawKey = redis.getStringSerializer().serialize("recovery:" + sessionId);
                        byte[] rawVal = conn.stringCommands().getDel(Objects.requireNonNull(rawKey));
                        return rawVal != null ? new String(rawVal, java.nio.charset.StandardCharsets.UTF_8) : null;
                    });
            if (json == null) {
                return null;
            }
            return mapper.readValue(json, EmergencyRecoveryState.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("[Redis] Failed to deserialise EmergencyRecoveryState (GETDEL) for session '"
                    + sessionId + "': " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteEmergencyRecoveryState(String sessionId) {
        if (!redis.delete("recovery:" + sessionId)) {
            throw new AuthExceptions.InvalidCredentials("Temporarily saved EmergencyRecoveryState not found to delete");
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
    public String getAndDeleteValue(String key) {
        return redis.opsForValue().getAndDelete(key);
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
