package com.kerosene.auth.application.orchestrator.signup.port;

import java.time.Duration;

import com.kerosene.auth.dto.SignupState;
import com.kerosene.auth.dto.UserDTO;

/** Port for pending users and expiring signup workflow state. */
public interface SignupStateStore {

    /** Creates or updates the temporary pending user representation. */
    /** @param dto pending user data */
    void createPendingUser(UserDTO dto);

    /** Finds temporary pending user data matching the supplied lookup identity. */
    /** @param lookup lookup fields, typically the normalized username */
    /** @return pending user data or {@code null} if absent */
    UserDTO findPendingUser(UserDTO lookup);

    /** Deletes the pending user representation. */
    /** @param dto identity fields selecting the temporary user */
    void deletePendingUser(UserDTO dto);

    /** Saves signup workflow state for the requested duration. */
    /** @param sessionId signup session identifier */
    /** @param state current workflow state */
    /** @param ttl state lifetime */
    void saveSignupState(String sessionId, SignupState state, Duration ttl);

    /** Reads signup workflow state without consuming it. */
    /** @param sessionId signup session identifier */
    /** @return stored state or {@code null} when absent or expired */
    SignupState findSignupState(String sessionId);

    /** Atomically reads and removes signup state so it cannot be finalized twice. */
    /** @param sessionId signup session identifier */
    /** @return consumed state or {@code null} when absent or expired */
    SignupState consumeSignupState(String sessionId);

    /** Deletes signup state explicitly. */
    /** @param sessionId signup session identifier */
    void deleteSignupState(String sessionId);
}
