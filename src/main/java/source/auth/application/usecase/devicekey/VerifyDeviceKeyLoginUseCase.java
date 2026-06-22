package source.auth.application.usecase.devicekey;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import source.auth.application.infra.persistence.jpa.DeviceKeyCredentialRepository;
import source.auth.application.infra.persistence.jpa.UserRepository;
import source.auth.application.orchestrator.login.StartLogin;
import source.auth.application.orchestrator.signup.FinalizeSignupAccount;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.auth.application.service.devicekey.DeviceKeyService;
import source.auth.application.service.util.DevBalanceInjector;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import source.auth.dto.devicekey.DeviceKeyVerifyRequest;
import source.auth.model.entity.DeviceKeyCredential;
import source.auth.model.entity.UserDataBase;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Component
public class VerifyDeviceKeyLoginUseCase {

    private final DeviceKeyService deviceKeyService;
    private final DeviceKeyCredentialRepository deviceKeyRepository;
    private final UserRepository userRepository;
    private final FinalizeSignupAccount finalizeSignupAccount;
    private final JwtServicer jwtServicer;
    private final DevBalanceInjector balanceInjector;
    private final RedisServicer redisService;

    public VerifyDeviceKeyLoginUseCase(
            DeviceKeyService deviceKeyService,
            DeviceKeyCredentialRepository deviceKeyRepository,
            UserRepository userRepository,
            FinalizeSignupAccount finalizeSignupAccount,
            JwtServicer jwtServicer,
            DevBalanceInjector balanceInjector,
            RedisServicer redisService) {
        this.deviceKeyService = deviceKeyService;
        this.deviceKeyRepository = deviceKeyRepository;
        this.userRepository = userRepository;
        this.finalizeSignupAccount = finalizeSignupAccount;
        this.jwtServicer = jwtServicer;
        this.balanceInjector = balanceInjector;
        this.redisService = redisService;
    }

    @Transactional
    public Result execute(DeviceKeyVerifyRequest request) {
        if (request.getCredentialId() == null || request.getCredentialId().isBlank()) {
            return Result.invalidCredentialId();
        }

        UserDataBase user;
        Optional<DeviceKeyCredential> credentialOpt;
        String credentialId = request.getCredentialId().trim();
        String normalizedUsername = normalizeUsername(request.getUsername());
        if (normalizedUsername.isBlank()) {
            credentialOpt = deviceKeyRepository.findByCredentialId(credentialId);
            if (credentialOpt.isEmpty()) {
                return Result.credentialNotFound();
            }
            user = credentialOpt.get().getUser();
        } else {
            user = userRepository.findByUsername(normalizedUsername);
            if (user == null) {
                return Result.userNotFound();
            }
            credentialOpt = deviceKeyRepository.findByCredentialIdAndUserId(credentialId, user.getId());
        }

        if (credentialOpt.isEmpty()) {
            return Result.credentialNotFound();
        }

        DeviceKeyCredential credential = credentialOpt.get();
        long newCounter = deviceKeyService.verifyAuthentication(request, user, credential);
        int updated = deviceKeyRepository.advanceCounter(
                credential.getCredentialId(),
                user.getId(),
                newCounter,
                LocalDateTime.now());
        if (updated != 1) {
            return Result.replayCounterNotAdvanced();
        }

        finalizeSignupAccount.ensureUserFinancialsReady(user, null);

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            return Result.inactiveAccount();
        }

        if (user.hasTotpEnabled()) {
            String preAuthToken = UUID.randomUUID().toString();
            redisService.setValue(StartLogin.preAuthKey(preAuthToken), user.getUsername(), StartLogin.PRE_AUTH_TTL_SECONDS);
            return Result.totpRequired(preAuthToken);
        }

        balanceInjector.injectTestBalance(user);

        String token = jwtServicer.generateToken(user.getId());
        return Result.authenticated(token);
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    public record Result(Status status, Object data) {

        public static Result invalidCredentialId() {
            return new Result(Status.INVALID_CREDENTIAL_ID, null);
        }

        public static Result credentialNotFound() {
            return new Result(Status.CREDENTIAL_NOT_FOUND, null);
        }

        public static Result userNotFound() {
            return new Result(Status.USER_NOT_FOUND, null);
        }

        public static Result replayCounterNotAdvanced() {
            return new Result(Status.REPLAY_COUNTER_NOT_ADVANCED, null);
        }

        public static Result inactiveAccount() {
            return new Result(Status.INACTIVE_ACCOUNT, null);
        }

        public static Result totpRequired(String preAuthToken) {
            return new Result(Status.TOTP_REQUIRED, preAuthToken);
        }

        public static Result authenticated(String token) {
            return new Result(Status.AUTHENTICATED, token);
        }
    }

    public enum Status {
        INVALID_CREDENTIAL_ID,
        CREDENTIAL_NOT_FOUND,
        USER_NOT_FOUND,
        REPLAY_COUNTER_NOT_ADVANCED,
        INACTIVE_ACCOUNT,
        TOTP_REQUIRED,
        AUTHENTICATED
    }
}
