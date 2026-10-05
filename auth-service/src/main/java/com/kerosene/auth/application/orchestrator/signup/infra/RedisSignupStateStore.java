package com.kerosene.auth.application.orchestrator.signup.infra;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.kerosene.auth.application.infra.persistence.redis.contracts.RedisContract;
import com.kerosene.auth.application.orchestrator.signup.port.SignupStateStore;
import com.kerosene.auth.application.service.cache.contracts.RedisServicer;
import com.kerosene.auth.dto.SignupState;
import com.kerosene.auth.dto.UserDTO;

/** Implements signup temporary storage through the pending-user cache and Redis state contract. */
@Component
public class RedisSignupStateStore implements SignupStateStore {

    /** Cache for pending user records maintained by the authentication service. */
    private final RedisServicer tempUserCache;
    /** Redis repository for structured signup workflow state. */
    private final RedisContract redis;

    /** Creates the adapter with both legacy user-cache and structured state storage boundaries. */
    /** @param tempUserCache pending user cache service */
    /** @param redis Redis contract for signup state */
    public RedisSignupStateStore(RedisServicer tempUserCache, RedisContract redis) {
        this.tempUserCache = tempUserCache;
        this.redis = redis;
    }

    /** Stores temporary user data using the existing cache service. */
    /** @param dto pending user data */
    @Override
    public void createPendingUser(UserDTO dto) {
        tempUserCache.createTempUser(dto);
    }

    /** Loads temporary user data matching the lookup DTO. */
    /** @param lookup lookup identity */
    /** @return pending user data or {@code null} when absent */
    @Override
    public UserDTO findPendingUser(UserDTO lookup) {
        return tempUserCache.getFromRedis(lookup);
    }

    /** Deletes temporary user data from the cache. */
    /** @param dto identity selecting the cached pending user */
    @Override
    public void deletePendingUser(UserDTO dto) {
        tempUserCache.deleteFromRedis(dto);
    }

    /** Saves structured signup state, converting the duration to the Redis contract's minute unit. */
    /** @param sessionId signup session identifier */
    /** @param state signup workflow state */
    /** @param ttl requested state lifetime */
    @Override
    public void saveSignupState(String sessionId, SignupState state, Duration ttl) {
        redis.saveSignupState(sessionId, state, ttl.toMinutes());
    }

    /** Reads structured signup state without consuming it. */
    /** @param sessionId signup session identifier */
    /** @return state or {@code null} when absent */
    @Override
    public SignupState findSignupState(String sessionId) {
        return redis.findSignupState(sessionId);
    }

    /** Reads and atomically removes signup state through Redis GETDEL. */
    /** @param sessionId signup session identifier */
    /** @return consumed state or {@code null} when absent */
    @Override
    public SignupState consumeSignupState(String sessionId) {
        return redis.getdelSignupState(sessionId);
    }

    /** Deletes signup state using the Redis contract's explicit deletion operation. */
    /** @param sessionId signup session identifier */
    @Override
    public void deleteSignupState(String sessionId) {
        redis.deleteSignupState(sessionId);
    }
}
