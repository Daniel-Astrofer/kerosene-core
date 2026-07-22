package source.auth.application.orchestrator.login;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import source.auth.AuthExceptions;
import source.auth.application.service.authentication.contracts.LoginVerifier;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.auth.application.service.cripto.contracts.Hasher;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.application.service.validation.totp.contracts.TOTPVerifier;
import source.auth.dto.contracts.UserDTOContract;
import source.auth.model.entity.UserDataBase;

@Component
public class VerifySecondFactor {

    private final LoginVerifier verifier;
    private final TOTPVerifier totpVerifier;
    private final UserServiceContract userService;
    private final RedisServicer redisService;
    private final Hasher hasher;
    private final LoginThrottlePolicy throttlePolicy;

    public VerifySecondFactor(LoginVerifier verifier,
            TOTPVerifier totpVerifier,
            UserServiceContract userService,
            RedisServicer redisService,
            @Qualifier("Argon2Hasher") Hasher hasher,
            LoginThrottlePolicy throttlePolicy) {
        this.verifier = verifier;
        this.totpVerifier = totpVerifier;
        this.userService = userService;
        this.redisService = redisService;
        this.hasher = hasher;
        this.throttlePolicy = throttlePolicy;
    }

    public UserDataBase verify(UserDTOContract dto) {
        String preAuthToken = requirePreAuthToken(dto);
        String username = redisService.getValue(StartLogin.preAuthKey(preAuthToken));
        if (username == null) {
            throw new AuthExceptions.InvalidCredentials("Sessão expirada. Faça login novamente.");
        }

        String throttleUsername = username.toLowerCase(Locale.ROOT);
        throttlePolicy.ensureSecondFactorAllowed(throttleUsername);

        UserDataBase user = verifier.findByUsernameOnly(username);
        throttlePolicy.ensureEmergencyTotpAllowed(user);

        String code = requireSecondFactorCode(dto);
        try {
            verifyCode(user, code);
            throttlePolicy.recordSecondFactorSuccess(throttleUsername, user);
        } catch (Exception e) {
            throttlePolicy.recordSecondFactorFailure(throttleUsername, user);
            throw e;
        }

        redisService.deleteValue(StartLogin.preAuthKey(preAuthToken));
        return user;
    }

    private String requirePreAuthToken(UserDTOContract dto) {
        if (dto == null || dto.getPreAuthToken() == null || dto.getPreAuthToken().isEmpty()) {
            throw new AuthExceptions.InvalidCredentials("Pre-Auth token required.");
        }
        return dto.getPreAuthToken();
    }

    private String requireSecondFactorCode(UserDTOContract dto) {
        if (dto.getTotpCode() == null || dto.getTotpCode().isEmpty()) {
            throw new AuthExceptions.InvalidCredentials("TOTP/Backup code required.");
        }
        return dto.getTotpCode();
    }

    private void verifyCode(UserDataBase user, String code) {
        if (matchesTotp(user, code) || matchesBackupCode(user, code)) {
            return;
        }
        throw new AuthExceptions.InvalidCredentials("Invalid TOTP or Backup code.");
    }

    private boolean matchesTotp(UserDataBase user, String code) {
        try {
            totpVerifier.totpVerify(user.getTOTPSecret(), code);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean matchesBackupCode(UserDataBase user, String code) {
        if (code.length() != 8 || user.getBackupCodes() == null) {
            return false;
        }

        char[] backupCode = code.toCharArray();
        try {
            Iterator<String> it = user.getBackupCodes().iterator();
            while (it.hasNext()) {
                String hash = it.next();
                if (hasher.verify(backupCode, hash)) {
                    it.remove();
                    userService.createUserInDataBase(user);
                    return true;
                }
            }
            return false;
        } finally {
            Arrays.fill(backupCode, '\0');
        }
    }
}
