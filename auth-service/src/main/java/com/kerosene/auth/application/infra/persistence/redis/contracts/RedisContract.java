package com.kerosene.auth.application.infra.persistence.redis.contracts;

import com.kerosene.auth.dto.UserDTO;
import com.kerosene.auth.dto.SignupState;
import com.kerosene.auth.dto.EmergencyRecoveryState;

/**
 * Port for Redis-backed temporary authentication records and shared security values.
 * Implementations define serialization, TTL handling, and outage behavior; callers must
 * treat nullable reads and counters according to the relevant authentication flow.
 */
public interface RedisContract {

    /** Stores a pending user record for the supplied lifetime. */
    /** @param key key prefix combined with the DTO username */
    /** @param dto temporary user state to persist */
    /** @param expirationInMinutes record lifetime in minutes */
    void save(String key, UserDTO dto, long expirationInMinutes);

    /** Reads a pending user record without consuming it. */
    /** @param key prefix used when the record was saved */
    /** @param dto object supplying the username used to complete the key */
    /** @return decoded user state, or {@code null} when absent */
    UserDTO find(String key, UserDTO dto);

    /** Deletes a pending user record. */
    /** @param key prefix used when the record was saved */
    /** @param dto object supplying the username used to complete the key */
    void delete(String key, UserDTO dto);

    /** Persists signup workflow state under its session identifier. */
    /** @param sessionId signup session identifier */
    /** @param state workflow state to persist */
    /** @param expirationInMinutes state lifetime in minutes */
    void saveSignupState(String sessionId, SignupState state, long expirationInMinutes);

    /** Reads signup state without consuming it. */
    /** @param sessionId signup session identifier */
    /** @return decoded state, or {@code null} when absent */
    SignupState findSignupState(String sessionId);

    /**
     * Atomically fetches and deletes the SignupState in a single Redis GETDEL call.
     */
    SignupState getdelSignupState(String sessionId);

    /** Deletes signup state explicitly. */
    /** @param sessionId signup session identifier */
    void deleteSignupState(String sessionId);

    /** Persists emergency-recovery state under its session identifier. */
    /** @param sessionId recovery session identifier */
    /** @param state recovery workflow state to persist */
    /** @param expirationInMinutes state lifetime in minutes */
    void saveEmergencyRecoveryState(String sessionId, EmergencyRecoveryState state, long expirationInMinutes);

    /** Reads emergency-recovery state without consuming it. */
    /** @param sessionId recovery session identifier */
    /** @return decoded state, or {@code null} when absent */
    EmergencyRecoveryState findEmergencyRecoveryState(String sessionId);

    /**
     * Atomically fetches and deletes emergency-recovery state in a single Redis GETDEL call.
     * This prevents two concurrent requests from reusing a one-time recovery challenge.
     *
     * @param sessionId recovery session identifier
     * @return consumed state, or {@code null} when absent
     */
    EmergencyRecoveryState getdelEmergencyRecoveryState(String sessionId);

    /** Deletes emergency-recovery state explicitly. */
    /** @param sessionId recovery session identifier */
    void deleteEmergencyRecoveryState(String sessionId);

    /** Increments a shared security counter. */
    /** @param key counter key */
    /** @return new count, or {@code null} when the implementation cannot provide one */
    Long increment(String key);

    /** Increments a counter and assigns its TTL on first creation when supported. */
    /** @param key counter key */
    /** @param timeoutSeconds expiry interval in seconds */
    /** @return new count, or {@code null} when the implementation cannot provide one */
    Long incrementWithExpire(String key, long timeoutSeconds);

    /** Applies or refreshes a key's expiry. */
    /** @param key Redis key */
    /** @param timeoutSeconds remaining lifetime in seconds */
    void expire(String key, long timeoutSeconds);

    /** Reads a generic string value. */
    /** @param key Redis key */
    /** @return stored value, or {@code null} when absent */
    String getValue(String key);

    /** Reads and removes a generic string value as one operation when supported. */
    /** @param key Redis key */
    /** @return previous value, or {@code null} when absent */
    String getAndDeleteValue(String key);

    /** Stores a generic string value with a seconds-based lifetime. */
    /** @param key Redis key */
    /** @param value value to store */
    /** @param timeoutSeconds expiry interval in seconds */
    void setValue(String key, String value, long timeoutSeconds);

    /** Deletes a generic value; implementations may treat absence as a no-op. */
    /** @param key Redis key */
    void deleteValue(String key);
}
