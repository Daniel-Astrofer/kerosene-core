package source.auth.application.orchestrator.signup.port;

import java.time.Duration;

import source.auth.dto.SignupState;
import source.auth.dto.UserDTO;

public interface SignupStateStore {

    void createPendingUser(UserDTO dto);

    UserDTO findPendingUser(UserDTO lookup);

    void deletePendingUser(UserDTO dto);

    void saveSignupState(String sessionId, SignupState state, Duration ttl);

    SignupState findSignupState(String sessionId);

    SignupState consumeSignupState(String sessionId);

    void deleteSignupState(String sessionId);
}
