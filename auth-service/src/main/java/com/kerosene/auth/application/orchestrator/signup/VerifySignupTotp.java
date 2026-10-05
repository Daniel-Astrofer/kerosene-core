package com.kerosene.auth.application.orchestrator.signup;

import org.springframework.stereotype.Component;

import com.kerosene.auth.AuthConstants;
import com.kerosene.auth.AuthExceptions;
import com.kerosene.auth.application.orchestrator.signup.port.SignupStateStore;
import com.kerosene.auth.application.service.validation.totp.contracts.TOTPVerifier;
import com.kerosene.auth.dto.SignupState;
import com.kerosene.auth.dto.UserDTO;

/** Verifies an optional TOTP enrollment code and records verification in pending signup state. */
@Component
public class VerifySignupTotp {

    /** Validates time-based one-time passwords against the secret generated for signup. */
    private final TOTPVerifier totpVerifier;
    /** Reads and refreshes pending signup state. */
    private final SignupStateStore stateStore;

    /** Creates the signup TOTP verifier. */
    /** @param totpVerifier TOTP code verifier */
    /** @param stateStore pending signup state boundary */
    public VerifySignupTotp(TOTPVerifier totpVerifier, SignupStateStore stateStore) {
        this.totpVerifier = totpVerifier;
        this.stateStore = stateStore;
    }

    /**
     * Requires a live signup session. A blank code records an unverified state; otherwise the
     * code is checked and the state is marked verified. Both outcomes refresh the state for 24 hours.
     *
     * @param dto signup DTO containing session ID and optional TOTP code
     * @return the validated signup session ID
     * @throws AuthExceptions.InvalidCredentials when the session ID is missing
     * @throws AuthExceptions.TotpTimeExceededException when the session state expired
     */
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
