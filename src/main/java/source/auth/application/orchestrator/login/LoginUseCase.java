package source.auth.application.orchestrator.login;

import source.auth.AuthExceptions;
import source.auth.application.orchestrator.login.contracts.Login;
import source.auth.application.service.authentication.contracts.LoginVerifier;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import source.auth.application.service.validation.totp.contratcs.TOTPVerifier;
import source.auth.dto.contracts.UserDTOContract;
import source.auth.model.entity.UserDataBase;
import source.auth.application.service.cripto.contracts.Hasher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Component
public class LoginUseCase implements Login {

    private static final Logger log = LoggerFactory.getLogger(LoginUseCase.class);

    private final LoginVerifier verifier;
    private final JwtServicer service;
    private final UserServiceContract userService;
    private final TOTPVerifier totpVerifier;
    private final source.notification.service.NotificationService notificationService;
    private final RedisServicer redisService;
    private final Hasher hasher;

    public LoginUseCase(LoginVerifier verifier, JwtServicer service,
            UserServiceContract userService, TOTPVerifier totpVerifier,
            source.notification.service.NotificationService notificationService,
            RedisServicer redisService,
            @org.springframework.beans.factory.annotation.Qualifier("Argon2Hasher") Hasher hasher) {
        this.verifier = verifier;
        this.service = service;
        this.userService = userService;
        this.totpVerifier = totpVerifier;
        this.notificationService = notificationService;
        this.redisService = redisService;
        this.hasher = hasher;
    }

    @Override
    public String loginUser(UserDTOContract dto) {
        if (dto.getUsername() == null) {
            throw new AuthExceptions.InvalidCredentials("Username required.");
        }
        String username = dto.getUsername().toLowerCase();
        String key = "login_failures:" + username;

        String failuresStr = redisService.getValue(key);
        int failures = failuresStr != null ? Integer.parseInt(failuresStr) : 0;

        if (failures >= 5) {
            throw new AuthExceptions.InvalidCredentials("Muitas tentativas falhas. Conta bloqueada por 15 minutos.");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && !auth.getName().equalsIgnoreCase("anonymousUser")) {
            throw new AuthExceptions.InvalidCredentials("Usuário já está autenticado.");
        }

        try {
            UserDataBase user = verifier.matcherWithoutDevice(dto);

            // Generate Pre-Auth Token instead of JWT
            String preAuthToken = UUID.randomUUID().toString();
            redisService.setValue("pre_auth:" + preAuthToken, user.getUsername(), 300); // 5 minutes

            redisService.expire(key, 0); // clear login failures
            return preAuthToken;
        } catch (AuthExceptions.InvalidCredentials e) {
            redisService.increment(key);
            redisService.expire(key, 15 * 60); // 15 minutes
            throw e;
        }
    }

    @Override
    public String loginTotpVerify(UserDTOContract dto) {
        if (dto.getPreAuthToken() == null || dto.getPreAuthToken().isEmpty()) {
            throw new AuthExceptions.InvalidCredentials("Pre-Auth token required.");
        }

        String username = redisService.getValue("pre_auth:" + dto.getPreAuthToken());
        if (username == null) {
            throw new AuthExceptions.InvalidCredentials("Sessão expirada. Faça login novamente.");
        }

        String blockKey = "totp_block:" + username.toLowerCase();
        String attemptKey = "totp_attempts:" + username.toLowerCase();

        if (redisService.getValue(blockKey) != null) {
            throw new AuthExceptions.InvalidCredentials("Muitas tentativas falhas. TOTP bloqueado por 5 minutos.");
        }

        UserDataBase user = verifier.findByUsernameOnly(username);

        if (user.getFailedLoginAttempts() >= 10) {
            throw new AuthExceptions.InvalidCredentials(
                    "Conta bloqueada emergencialmente por segurança. O uso do TOTP foi desativado. Resgate manual necessário.");
        }

        if (dto.getTotpCode() == null || dto.getTotpCode().isEmpty()) {
            throw new AuthExceptions.InvalidCredentials("TOTP/Backup code required.");
        }

        try {
            boolean matchedTotp = false;
            boolean matchedBackup = false;

            try {
                totpVerifier.totpVerify(user.getTOTPSecret(), dto.getTotpCode());
                matchedTotp = true;
            } catch (Exception ignored) {
                // Not a valid TOTP. Maybe it's a backup code?
            }

            if (!matchedTotp && dto.getTotpCode().length() == 8 && user.getBackupCodes() != null) {
                java.util.Iterator<String> it = user.getBackupCodes().iterator();
                while (it.hasNext()) {
                    String hash = it.next();
                    if (hasher.verify(dto.getTotpCode().toCharArray(), hash)) {
                        matchedBackup = true;
                        it.remove();
                        userService.createUserInDataBase(user); // Save used code
                        break;
                    }
                }
            }

            if (!matchedTotp && !matchedBackup) {
                throw new AuthExceptions.InvalidCredentials("Invalid TOTP or Backup code.");
            }

            // Sucesso: Zera as tentativas e o histórico de bloqueio
            redisService.deleteValue(attemptKey);
            user.setFailedLoginAttempts(0);
            userService.createUserInDataBase(user);

        } catch (Exception e) {
            redisService.increment(attemptKey);
            String attemptsStr = redisService.getValue(attemptKey);
            int currentAttempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 1;

            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            userService.createUserInDataBase(user);

            if (currentAttempts >= 3) {
                redisService.setValue(blockKey, "BLOCKED", 300); // 5 minutos de block
                redisService.deleteValue(attemptKey);
            }
            throw e;
        }

        redisService.deleteValue("pre_auth:" + dto.getPreAuthToken());

        notifyLogin(user.getId());
        return user.getId() + " " + service.generateToken(user.getId());
    }

    private void notifyLogin(Long userId) {
        try {
            notificationService.notifyUser(userId, "Acesso Detectado",
                    "Um novo acesso foi identificado em sua conta Kerosene. Caso não reconheça esta ação, verifique suas sessões ativas imediatamente.");
        } catch (Exception e) {
            log.warn("Falha ao enviar notificação de login para usuário {}", userId, e);
        }
    }
}
