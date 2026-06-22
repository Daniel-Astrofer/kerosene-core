package source.auth.application.usecase.devicekey;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import source.auth.application.infra.persistence.jpa.DeviceKeyCredentialRepository;
import source.auth.application.orchestrator.signup.FinalizeSignupAccount;
import source.auth.application.orchestrator.signup.port.SignupStateStore;
import source.auth.application.service.devicekey.DeviceKeyService;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import source.auth.dto.SignupState;
import source.auth.dto.devicekey.DeviceKeyRegistrationRequest;
import source.auth.model.entity.DeviceKeyCredential;
import source.auth.model.entity.UserDataBase;

import java.time.Duration;

@Component
public class FinishOnboardingDeviceKeyRegistrationUseCase {

    private static final Duration SIGNUP_STATE_TTL = Duration.ofMinutes(1440);

    private final SignupStateStore signupStateStore;
    private final DeviceKeyService deviceKeyService;
    private final FinalizeSignupAccount finalizeSignupAccount;
    private final DeviceKeyCredentialRepository deviceKeyRepository;
    private final JwtServicer jwtServicer;

    public FinishOnboardingDeviceKeyRegistrationUseCase(
            SignupStateStore signupStateStore,
            DeviceKeyService deviceKeyService,
            FinalizeSignupAccount finalizeSignupAccount,
            DeviceKeyCredentialRepository deviceKeyRepository,
            JwtServicer jwtServicer) {
        this.signupStateStore = signupStateStore;
        this.deviceKeyService = deviceKeyService;
        this.finalizeSignupAccount = finalizeSignupAccount;
        this.deviceKeyRepository = deviceKeyRepository;
        this.jwtServicer = jwtServicer;
    }

    @Transactional
    public Result execute(String sessionId, DeviceKeyRegistrationRequest request) {
        SignupState state = signupStateStore.findSignupState(sessionId);
        if (state == null) {
            return Result.sessionExpired();
        }

        DeviceKeyService.VerifiedDeviceKeyRegistration verified =
                deviceKeyService.verifyRegistration(request, sessionId, state.getUsername());

        state.setDeviceKeyRegistered(true);
        state.setPasskeyRegistered(true);
        signupStateStore.saveSignupState(sessionId, state, SIGNUP_STATE_TTL);

        UserDataBase user = finalizeSignupAccount.execute(sessionId);
        persistDeviceKey(user, verified);

        String token = user.getId() + " " + jwtServicer.generateToken(user.getId());
        return Result.created(token);
    }

    private void persistDeviceKey(
            UserDataBase user,
            DeviceKeyService.VerifiedDeviceKeyRegistration verified) {
        if (deviceKeyRepository.findByCredentialIdAndUserId(verified.credentialId(), user.getId()).isPresent()) {
            return;
        }

        DeviceKeyCredential credential = new DeviceKeyCredential();
        credential.setUser(user);
        credential.setCredentialId(verified.credentialId());
        credential.setUserHandle(verified.userHandle());
        credential.setPublicKeyEd25519(verified.publicKeyEd25519());
        credential.setAlgorithm(DeviceKeyService.ALGORITHM);
        credential.setCounter(verified.counter());
        credential.setDeviceName(verified.deviceName());
        credential.setDeviceInstallId(verified.deviceInstallId());
        credential.setKeyStorage(verified.keyStorage());
        credential.setPlatform(verified.platform());
        credential.setBrowser(verified.browser());
        credential.setBrand(verified.brand());
        credential.setModel(verified.model());
        credential.setSerialNumber(verified.serialNumber());
        credential.setOnionServiceId(verified.onionServiceId());
        credential.setProtocolVersion(1);
        credential.setStatus("ACTIVE");
        deviceKeyRepository.save(credential);
    }

    public record Result(Status status, String token) {

        public static Result created(String token) {
            return new Result(Status.CREATED, token);
        }

        public static Result sessionExpired() {
            return new Result(Status.SESSION_EXPIRED, null);
        }
    }

    public enum Status {
        CREATED,
        SESSION_EXPIRED
    }
}
