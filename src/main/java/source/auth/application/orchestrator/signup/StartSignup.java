package source.auth.application.orchestrator.signup;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import source.auth.AuthConstants;
import source.auth.AuthExceptions;
import source.auth.application.orchestrator.signup.port.SignupStateStore;
import source.auth.application.service.authentication.contracts.SignupVerifier;
import source.auth.application.service.cripto.contracts.Hasher;
import source.auth.application.service.pow.PowService;
import source.auth.application.service.security.profile.AccountSecurityProfileResolver;
import source.auth.application.service.validation.totp.contracts.TOTPKeyGenerate;
import source.auth.dto.SignupState;
import source.auth.dto.SignupResponseDTO;
import source.auth.dto.UserDTO;

@Component
public class StartSignup {

    private static final int BACKUP_CODE_COUNT = 10;
    private static final int BACKUP_CODE_BOUND = 100_000_000;
    private static final Duration SIGNUP_STATE_TTL = Duration.ofHours(24);

    private final TOTPKeyGenerate totpGenerator;
    private final SignupVerifier verifier;
    private final SignupStateStore stateStore;
    private final PowService powService;
    private final Hasher hasher;
    private final AccountSecurityProfileResolver accountSecurityProfileResolver;
    private final SecureRandom random = new SecureRandom();

    public StartSignup(TOTPKeyGenerate totpGenerator,
            SignupVerifier verifier,
            SignupStateStore stateStore,
            PowService powService,
            AccountSecurityProfileResolver accountSecurityProfileResolver,
            @Qualifier("Argon2Hasher") Hasher hasher) {
        this.totpGenerator = totpGenerator;
        this.verifier = verifier;
        this.stateStore = stateStore;
        this.powService = powService;
        this.accountSecurityProfileResolver = accountSecurityProfileResolver;
        this.hasher = hasher;
    }

    public SignupResponseDTO execute(UserDTO dto) {
        if (!powService.verifyChallenge(dto.getChallenge(), dto.getNonce())) {
            throw new AuthExceptions.InvalidCredentials(
                    "Invalid or expired Proof of Work. Please request a new challenge and calculate the correct nonce.");
        }

        String normalizedUsername = dto.getUsername().toLowerCase(Locale.ROOT);
        dto.setUsername(normalizedUsername);

        verifier.verify(dto.getUsername(), dto.getPassphrase());
        accountSecurityProfileResolver.normalize(dto);

        String totpKey = totpGenerator.keyGenerator();
        String otpUri = String.format(
                AuthConstants.TOTP_URI_FORMAT,
                AuthConstants.APP_NAME,
                dto.getUsername(),
                totpKey,
                AuthConstants.APP_NAME);

        BackupCodes backupCodes = generateBackupCodes();

        char[] passphrase = dto.getPassphrase();
        String hashedPassphrase = hasher.hash(passphrase);
        if (passphrase != null) {
            Arrays.fill(passphrase, '\0');
        }

        String sessionId = UUID.randomUUID().toString().replace("-", "");

        SignupState state = new SignupState();
        state.setSessionId(sessionId);
        state.setUsername(normalizedUsername);
        state.setPassphrase(hashedPassphrase.toCharArray());
        state.setTotpSecret(totpKey);
        state.setTotpVerified(false);
        state.setPasskeyRegistered(false);
        state.setPaymentConfirmed(false);
        state.setAccountSecurity(dto.getAccountSecurity());
        state.setShamirTotalShares(dto.getShamirTotalShares());
        state.setShamirThreshold(dto.getShamirThreshold());
        state.setMultisigThreshold(dto.getMultisigThreshold());
        state.setBackupCodes(backupCodes.hashedCodes());
        stateStore.saveSignupState(sessionId, state, SIGNUP_STATE_TTL);

        return new SignupResponseDTO(sessionId, otpUri, backupCodes.rawCodes(), true);
    }

    private BackupCodes generateBackupCodes() {
        List<String> rawCodes = new ArrayList<>();
        List<String> hashedCodes = new ArrayList<>();

        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            String code = String.format("%08d", random.nextInt(BACKUP_CODE_BOUND));
            rawCodes.add(code);
            char[] codeChars = code.toCharArray();
            try {
                hashedCodes.add(hasher.hash(codeChars));
            } finally {
                Arrays.fill(codeChars, '\0');
            }
        }

        return new BackupCodes(rawCodes, hashedCodes);
    }

    private record BackupCodes(List<String> rawCodes, List<String> hashedCodes) {
    }
}
