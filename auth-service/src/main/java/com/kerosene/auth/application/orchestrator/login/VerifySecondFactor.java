package com.kerosene.auth.application.orchestrator.login;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.kerosene.auth.AuthExceptions;
import com.kerosene.auth.application.service.authentication.contracts.LoginVerifier;
import com.kerosene.auth.application.service.cache.contracts.RedisServicer;
import com.kerosene.auth.application.service.crypto.contracts.Hasher;
import com.kerosene.auth.application.service.user.contract.UserServiceContract;
import com.kerosene.auth.application.service.validation.totp.contracts.TOTPVerifier;
import com.kerosene.auth.dto.contracts.UserDTOContract;
import com.kerosene.auth.model.entity.UserDataBase;

/** Verifies TOTP or a one-time backup code for a Redis-backed pending login. */
@Component
public class VerifySecondFactor {

    /** Credential lookup boundary for pending authenticated users. */
    private final LoginVerifier verifier;
    /** Verifier for time-based one-time passwords. */
    private final TOTPVerifier totpVerifier;
    /** Persistence boundary used when a backup code is consumed. */
    private final UserServiceContract userService;
    /** Redis boundary holding pending authentication and throttle state. */
    private final RedisServicer redisService;
    /** Argon2-configured verifier for stored one-time backup-code hashes. */
    private final Hasher hasher;
    /** Policy for temporary and durable second-factor failure handling. */
    private final LoginThrottlePolicy throttlePolicy;

    /**
     * Creates the second-factor verifier and its required authentication dependencies.
     *
     * @param verifier user lookup for an already credential-checked account
     * @param totpVerifier TOTP verification service
     * @param userService persistence boundary for consuming backup codes
     * @param redisService storage for pre-authentication and throttle markers
     * @param hasher hash verifier qualified for Argon2 backup-code hashes
     * @param throttlePolicy second-factor attempt policy
     */
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

    /**
     * Resolves the pending user, enforces blocks, checks TOTP or a backup code, then consumes
     * the pre-authentication key after successful verification.
     *
     * @param dto request carrying pre-auth token and second-factor code
     * @return verified user whose session may now be issued
     * @throws AuthExceptions.InvalidCredentials when the token/code is missing, expired, blocked,
     *         or invalid
     */
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

    /** Requires a non-empty pending pre-authentication token from the request. */
    /** @param dto second-factor request */
    /** @return pending token */
    /** @throws AuthExceptions.InvalidCredentials when token is missing */
    private String requirePreAuthToken(UserDTOContract dto) {
        if (dto == null || dto.getPreAuthToken() == null || dto.getPreAuthToken().isEmpty()) {
            throw new AuthExceptions.InvalidCredentials("Pre-Auth token required.");
        }
        return dto.getPreAuthToken();
    }

    /** Requires a non-empty submitted TOTP or backup code. */
    /** @param dto second-factor request */
    /** @return submitted factor code */
    /** @throws AuthExceptions.InvalidCredentials when the code is missing */
    private String requireSecondFactorCode(UserDTOContract dto) {
        if (dto.getTotpCode() == null || dto.getTotpCode().isEmpty()) {
            throw new AuthExceptions.InvalidCredentials("TOTP/Backup code required.");
        }
        return dto.getTotpCode();
    }

    /** Accepts either a valid TOTP or backup code and rejects the request if both fail. */
    /** @param user account whose configured factors are checked */
    /** @param code submitted TOTP or backup code */
    /** @throws AuthExceptions.InvalidCredentials when no configured factor matches */
    private void verifyCode(UserDataBase user, String code) {
        if (matchesTotp(user, code) || matchesBackupCode(user, code)) {
            return;
        }
        throw new AuthExceptions.InvalidCredentials("Invalid TOTP or Backup code.");
    }

    /** Returns whether the submitted value passes TOTP verification; verifier exceptions mean no match. */
    /** @param user account containing the encrypted/configured TOTP secret */
    /** @param code submitted candidate code */
    /** @return {@code true} when TOTP verification succeeds */
    private boolean matchesTotp(UserDataBase user, String code) {
        try {
            totpVerifier.totpVerify(user.getTOTPSecret(), code);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Checks an eight-character backup code against stored hashes, removes and persists the first
     * match, and clears the temporary character array before returning.
     *
     * @param user account whose backup-code hashes may be consumed
     * @param code submitted backup-code candidate
     * @return {@code true} when a stored hash matches and is consumed
     */
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
