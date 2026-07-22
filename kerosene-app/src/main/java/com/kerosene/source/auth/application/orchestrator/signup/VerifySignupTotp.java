package source.auth.application.orchestrator.signup;

import org.springframework.stereotype.Component;

import source.auth.AuthConstants;
import source.auth.AuthExceptions;
import source.auth.application.orchestrator.signup.port.SignupStateStore;
import source.auth.application.service.validation.totp.contracts.TOTPVerifier;
import source.auth.dto.SignupState;
import source.auth.dto.UserDTO;

@Component
public class VerifySignupTotp {

    private final TOTPVerifier totpVerifier;
    private final SignupStateStore stateStore;

    public VerifySignupTotp(TOTPVerifier totpVerifier, SignupStateStore stateStore) {
        this.totpVerifier = totpVerifier;
        this.stateStore = stateStore;
    }

    public String execute(UserDTO dto) {
        if (dto.getSessionId() == null || dto.getSessionId().isBlank()) {
            throw new AuthExceptions.InvalidCredentials("Signup sessionId required.");
        }

        SignupState state = stateStore.findSignupState(dto.getSessionId());
        if (state == null) {
            throw new AuthExceptions.TotpTimeExceededException(AuthConstants.ERR_TOTP_EXPIRED);
        }

        if (dto.getTotpCode() == null || dto.getTotpCode().isBlank()) {
            state.setTotpVerified(false);
            stateStore.saveSignupState(dto.getSessionId(), state, java.time.Duration.ofHours(24));
            return dto.getSessionId();
        }

        totpVerifier.totpVerify(state.getTotpSecret(), dto.getTotpCode());
        state.setTotpVerified(true);
        stateStore.saveSignupState(dto.getSessionId(), state, java.time.Duration.ofHours(24));
        return dto.getSessionId();
    }
}
