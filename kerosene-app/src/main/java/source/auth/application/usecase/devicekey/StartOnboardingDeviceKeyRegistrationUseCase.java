package source.auth.application.usecase.devicekey;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import source.auth.application.orchestrator.signup.port.SignupStateStore;
import source.auth.application.service.devicekey.DeviceKeyService;
import source.auth.dto.SignupState;
import source.auth.dto.devicekey.DeviceKeyChallengeResponse;

import java.time.Duration;
import java.util.Locale;

@Component
public class StartOnboardingDeviceKeyRegistrationUseCase {

    private static final Duration SIGNUP_STATE_TTL = Duration.ofMinutes(1440);

    private final SignupStateStore signupStateStore;
    private final DeviceKeyService deviceKeyService;

    public StartOnboardingDeviceKeyRegistrationUseCase(
            SignupStateStore signupStateStore,
            DeviceKeyService deviceKeyService) {
        this.signupStateStore = signupStateStore;
        this.deviceKeyService = deviceKeyService;
    }

    @Transactional
    public Result execute(String sessionId, String username) {
        SignupState state = signupStateStore.findSignupState(sessionId);
        if (state == null) {
            return Result.sessionExpired();
        }

        if ((state.getUsername() == null || state.getUsername().isBlank())
                && username != null && !username.isBlank()) {
            state.setUsername(normalizeUsername(username));
            signupStateStore.saveSignupState(sessionId, state, SIGNUP_STATE_TTL);
        }

        DeviceKeyChallengeResponse challenge = deviceKeyService.startRegistrationChallenge(
                sessionId,
                state.getUsername());
        return Result.generated(challenge);
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    public record Result(Status status, DeviceKeyChallengeResponse challenge) {

        public static Result generated(DeviceKeyChallengeResponse challenge) {
            return new Result(Status.GENERATED, challenge);
        }

        public static Result sessionExpired() {
            return new Result(Status.SESSION_EXPIRED, null);
        }
    }

    public enum Status {
        GENERATED,
        SESSION_EXPIRED
    }
}
