package source.auth.application.orchestrator.signup;

import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Component;

import source.auth.AuthConstants;
import source.auth.AuthExceptions;
import source.auth.application.orchestrator.signup.port.SignupStateStore;
import source.auth.application.service.validation.totp.contracts.TOTPVerifier;
import source.auth.dto.SignupState;
import source.auth.dto.UserDTO;
import source.auth.model.enums.AccountSecurityType;

@Component
public class VerifySignupTotp {

    private static final Duration SIGNUP_STATE_TTL = Duration.ofMinutes(1440);

    private final TOTPVerifier totpVerifier;
    private final SignupStateStore stateStore;

    public VerifySignupTotp(TOTPVerifier totpVerifier, SignupStateStore stateStore) {
        this.totpVerifier = totpVerifier;
        this.stateStore = stateStore;
    }

    public String execute(UserDTO dto) {
        UserDTO cachedUser = stateStore.findPendingUser(dto);

        if (cachedUser == null) {
            throw new AuthExceptions.TotpTimeExceededException(AuthConstants.ERR_TOTP_EXPIRED);
        }

        if (dto.getTotpCode() == null || dto.getTotpCode().isEmpty()) {
            throw new AuthExceptions.InvalidCredentials("TOTP code required to complete registration.");
        }
        totpVerifier.totpVerify(cachedUser.getTotpSecret(), dto.getTotpCode());

        String sessionId = UUID.randomUUID().toString().replace("-", "");
        SignupState state = buildSignupState(sessionId, cachedUser);

        stateStore.saveSignupState(sessionId, state, SIGNUP_STATE_TTL);
        stateStore.deletePendingUser(cachedUser);

        return sessionId;
    }

    private static SignupState buildSignupState(String sessionId, UserDTO cachedUser) {
        SignupState state = new SignupState();
        state.setSessionId(sessionId);
        state.setUsername(cachedUser.getUsername());
        state.setPassphrase(cachedUser.getPassphrase());
        state.setTotpSecret(cachedUser.getTotpSecret());
        state.setTotpVerified(true);
        state.setPasskeyRegistered(false);
        state.setPaymentConfirmed(false);
        state.setAccountSecurity(cachedUser.getAccountSecurity() != null
                ? cachedUser.getAccountSecurity()
                : AccountSecurityType.STANDARD);
        state.setShamirTotalShares(cachedUser.getShamirTotalShares());
        state.setShamirThreshold(cachedUser.getShamirThreshold());
        state.setMultisigThreshold(cachedUser.getMultisigThreshold());
        state.setBackupCodes(cachedUser.getBackupCodes());
        return state;
    }
}
