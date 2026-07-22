package source.auth.application.service.account;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.auth.AuthConstants;
import source.auth.AuthExceptions;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.application.service.validation.totp.contracts.TOTPKeyGenerate;
import source.auth.application.service.validation.totp.contracts.TOTPVerifier;
import source.auth.dto.BackupCodesStatusDTO;
import source.auth.dto.TotpSetupResponseDTO;
import source.auth.model.entity.UserDataBase;

@Service
public class TotpManagementService {

    private static final long SETUP_TTL_SECONDS = 600L;

    private final UserServiceContract userService;
    private final TOTPKeyGenerate totpKeyGenerate;
    private final TOTPVerifier totpVerifier;
    private final RedisServicer redisService;
    private final BackupCodeService backupCodeService;

    public TotpManagementService(
            UserServiceContract userService,
            TOTPKeyGenerate totpKeyGenerate,
            TOTPVerifier totpVerifier,
            RedisServicer redisService,
            BackupCodeService backupCodeService) {
        this.userService = userService;
        this.totpKeyGenerate = totpKeyGenerate;
        this.totpVerifier = totpVerifier;
        this.redisService = redisService;
        this.backupCodeService = backupCodeService;
    }

    public TotpSetupResponseDTO beginSetup(Long userId) {
        UserDataBase user = requireUser(userId);
        String secret = totpKeyGenerate.keyGenerator();
        redisService.setValue(tempSetupKey(userId), secret, SETUP_TTL_SECONDS);
        String otpUri = String.format(
                AuthConstants.TOTP_URI_FORMAT,
                AuthConstants.APP_NAME,
                user.getUsername(),
                secret,
                AuthConstants.APP_NAME);
        return new TotpSetupResponseDTO(otpUri, secret);
    }

    @Transactional
    public BackupCodesStatusDTO verifySetup(Long userId, String code) {
        String secret = redisService.getValue(tempSetupKey(userId));
        if (secret == null || secret.isBlank()) {
            throw new AuthExceptions.TotpTimeExceededException("TOTP setup session expired. Start setup again.");
        }
        if (code == null || code.isBlank()) {
            throw new AuthExceptions.IncorrectTotpException("TOTP code required.");
        }

        totpVerifier.totpVerify(secret, code);
        UserDataBase user = requireUser(userId);
        user.setTOTPSecret(secret);
        userService.createUserInDataBase(user);
        redisService.deleteValue(tempSetupKey(userId));
        return backupCodeService.regenerate(userId);
    }

    @Transactional
    public void disable(Long userId) {
        UserDataBase user = requireUser(userId);
        user.setTOTPSecret(null);
        user.setBackupCodes(java.util.Collections.emptyList());
        userService.createUserInDataBase(user);
        redisService.deleteValue(tempSetupKey(userId));
    }

    private UserDataBase requireUser(Long userId) {
        return userService.buscarPorId(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found."));
    }

    private String tempSetupKey(Long userId) {
        return "totp:setup:" + userId;
    }
}
