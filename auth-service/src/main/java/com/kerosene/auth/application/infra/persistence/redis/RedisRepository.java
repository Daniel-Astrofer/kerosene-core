package com.kerosene.auth.application.infra.persistence.redis;

import com.kerosene.auth.AuthExceptions;
import com.kerosene.auth.application.infra.persistence.redis.contracts.RedisContract;
import com.kerosene.auth.dto.UserDTO;
import com.kerosene.auth.dto.SignupState;
import com.kerosene.auth.dto.EmergencyRecoveryState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;
import java.util.Objects;

/**
 * Redis-backed implementation of temporary authentication state and shared counters.
 * Signup and recovery records use namespaced keys and explicit TTLs; the GETDEL methods
 * consume one-time state atomically, while counter helpers deliberately degrade to null
 * or a logged no-op for selected Redis outages.
 */
@Repository
public class RedisRepository implements RedisContract {
    /** Serializes structured temporary authentication records to and from JSON. */
    private final ObjectMapper mapper;
    /** Executes string-valued Redis operations and atomic scripts. */
    private final StringRedisTemplate redis;

    /** Creates the repository with the shared JSON mapper and Redis connection template. */
    /** @param mapper application-configured Jackson mapper */
    /** @param redis Redis template configured for string keys and values */
    public RedisRepository(ObjectMapper mapper, StringRedisTemplate redis) {
        this.mapper = mapper;
        this.redis = redis;
    }

    /**
     * Stores a pending user record as JSON using the username appended to the caller prefix.
     * Serialization failures are infrastructure errors and are surfaced as IllegalStateException.
     *
     * @param key key prefix supplied by the signup flow
     * @param dto temporary user data to serialize
     * @param expirationInMinutes TTL applied to the Redis value
     */
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

    /** Logger for repository diagnostics; counter logs use a hash reference instead of the raw key. */
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RedisRepository.class);

    /**
     * Reads and deserializes the pending user record addressed by prefix and username.
     *
     * @param key key prefix supplied by the signup flow
     * @param dto object whose username completes the key
     * @return stored user data, or {@code null} when the key is absent
     * @throws IllegalStateException when stored JSON cannot be deserialized
     */
    @Override
    public UserDTO find(String key, UserDTO dto) {
        try {
            log.debug("[RedisRepository] find() called for signup lookup");
            String json = redis.opsForValue().get(key + dto.getUsername());
            if (json == null) {
                return null;
            }
            return mapper.readValue(json, com.kerosene.auth.dto.UserDTO.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "[Redis] Failed to deserialise UserDTO for user '" + dto.getUsername() + "': " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a pending user record, reporting absence as invalid temporary credentials.
     *
     * @param key key prefix supplied by the signup flow
     * @param dto object whose username completes the key
     * @throws AuthExceptions.InvalidCredentials when no matching temporary record exists
     */
    public void delete(String key, UserDTO dto) {
        if (!redis.delete(key + dto.getUsername())) {
            throw new AuthExceptions.InvalidCredentials("Temporary user not found to delete");
        }
    }

    /** Stores signup state as JSON under its session namespace with a minute-based TTL. */
    /** @param sessionId signup session identifier used as the key suffix */
    /** @param state structured signup state to persist */
    /** @param expirationInMinutes lifetime of the state in minutes */
    @Override
    public void saveSignupState(String sessionId, SignupState state, long expirationInMinutes) {
        try {
            String json = mapper.writeValueAsString(state);
            log.info("[Redis] Saving signup state key=signup:{} ttl={}min jsonLen={}",
                    sessionId, expirationInMinutes, json.length());
            redis.opsForValue().set("signup:" + sessionId, json, expirationInMinutes, TimeUnit.MINUTES);
            log.info("[Redis] Saved signup state key=signup:{}", sessionId);
        } catch (JsonProcessingException e) {
            log.error("[Redis] Failed to serialise SignupState for session '{}': {}", sessionId, e.getMessage(), e);
            throw new IllegalStateException(
                    "[Redis] Failed to serialise SignupState for session '" + sessionId + "': " + e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("[Redis] Failed to save SignupState for session '{}': {}", sessionId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Reads signup state without consuming it.
     *
     * @param sessionId signup session identifier
     * @return decoded state, or {@code null} if no value exists
     * @throws IllegalStateException when stored JSON cannot be decoded
     */
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

    /**
     * Atomically reads and removes signup state with Redis GETDEL, preventing concurrent reuse.
     *
     * @param sessionId signup session identifier
     * @return consumed state, or {@code null} when absent
     * @throws IllegalStateException when the consumed JSON cannot be decoded
     */
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

    /** Removes signup state explicitly and reports when the expected temporary value is absent. */
    /** @param sessionId signup session identifier */
    /** @throws AuthExceptions.InvalidCredentials when no signup state was deleted */
    @Override
    public void deleteSignupState(String sessionId) {
        if (!redis.delete("signup:" + sessionId)) {
            throw new AuthExceptions.InvalidCredentials("Temporarily saved SignupState not found to delete");
        }
    }

    /** Persists emergency-recovery state in its own namespace with a minute-based TTL. */
    /** @param sessionId recovery session identifier */
    /** @param state recovery data to persist */
    /** @param expirationInMinutes lifetime of the state in minutes */
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

    /**
     * Reads emergency-recovery state without consuming it.
     *
     * @param sessionId recovery session identifier
     * @return decoded state, or {@code null} when absent
     * @throws IllegalStateException when stored JSON cannot be decoded
     */
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

    /**
     * Atomically reads and consumes emergency-recovery state with Redis GETDEL.
     *
     * @param sessionId recovery session identifier
     * @return consumed state, or {@code null} when absent
     * @throws IllegalStateException when consumed JSON cannot be decoded
     */
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

    /** Deletes recovery state explicitly and reports when the expected value is absent. */
    /** @param sessionId recovery session identifier */
    /** @throws AuthExceptions.InvalidCredentials when no recovery state was deleted */
    @Override
    public void deleteEmergencyRecoveryState(String sessionId) {
        if (!redis.delete("recovery:" + sessionId)) {
            throw new AuthExceptions.InvalidCredentials("Temporarily saved EmergencyRecoveryState not found to delete");
        }
    }

    /**
     * Increments a Redis counter. Redis runtime failures are logged with a hashed key reference
     * and return {@code null}, allowing callers to apply their documented degraded-mode policy.
     *
     * @param key counter key
     * @return incremented value, or {@code null} when Redis fails
     */
    @Override
    public Long increment(String key) {
        try {
            Long value = redis.opsForValue().increment(key);
            return value == null ? 1L : value;
        } catch (RuntimeException exception) {
            // Rate-limit and counters must not 500 the whole request when Redis is degraded
            // (e.g. MISCONF / stop-writes after disk-full bgsave). Callers may treat null as fail-open.
            log.warn("[Redis] increment failed for keyRef={}: {}",
                    key == null ? "null" : Integer.toHexString(key.hashCode()),
                    exception.getMessage());
            return null;
        }
    }

    /**
     * Atomically increments a counter and sets its expiry only on the first increment.
     * Redis failures are logged with a hashed key reference and return {@code null}.
     *
     * @param key counter key
     * @param timeoutSeconds TTL assigned on first increment
     * @return incremented value, or {@code null} when Redis fails
     */
    @Override
    public Long incrementWithExpire(String key, long timeoutSeconds) {
        try {
            // Atomic Lua: INCR + conditional EXPIRE on first increment
            String script = "local n = redis.call('INCR', KEYS[1]) "
                    + "if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return n";
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
            Long value = redis.execute(
                    redisScript,
                    java.util.List.of(key),
                    String.valueOf(timeoutSeconds));
            return value;
        } catch (RuntimeException exception) {
            log.warn("[Redis] incrementWithExpire failed for keyRef={}: {}",
                    key == null ? "null" : Integer.toHexString(key.hashCode()),
                    exception.getMessage());
            return null;
        }
    }

    /**
     * Applies a seconds-based TTL, logging Redis failures as a no-op with a hashed key reference.
     *
     * @param key Redis key to expire
     * @param timeoutSeconds requested remaining lifetime
     */
    @Override
    public void expire(String key, long timeoutSeconds) {
        try {
            redis.expire(key, timeoutSeconds, TimeUnit.SECONDS);
        } catch (RuntimeException exception) {
            log.warn("[Redis] expire failed for keyRef={}: {}",
                    key == null ? "null" : Integer.toHexString(key.hashCode()),
                    exception.getMessage());
        }
    }

    /** Reads an unstructured string value by key. */
    /** @param key Redis key */
    /** @return stored string or {@code null} when the key is absent */
    @Override
    public String getValue(String key) {
        return redis.opsForValue().get(key);
    }

    /** Reads and deletes a string value in the same Redis operation. */
    /** @param key Redis key */
    /** @return prior value or {@code null} when the key is absent */
    @Override
    public String getAndDeleteValue(String key) {
        return redis.opsForValue().getAndDelete(key);
    }

    /** Writes an unstructured string value with a seconds-based TTL. */
    /** @param key Redis key */
    /** @param value string value to store */
    /** @param timeoutSeconds TTL in seconds */
    @Override
    public void setValue(String key, String value, long timeoutSeconds) {
        redis.opsForValue().set(key, value, timeoutSeconds, TimeUnit.SECONDS);
    }

    /** Deletes an unstructured Redis value; absence is treated as an idempotent outcome. */
    /** @param key Redis key */
    @Override
    public void deleteValue(String key) {
        redis.delete(key);
    }
}
