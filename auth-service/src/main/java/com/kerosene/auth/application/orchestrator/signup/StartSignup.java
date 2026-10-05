package com.kerosene.auth.application.orchestrator.signup;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.kerosene.auth.AuthConstants;
import com.kerosene.auth.AuthExceptions;
import com.kerosene.auth.application.orchestrator.signup.port.SignupStateStore;
import com.kerosene.auth.application.service.authentication.contracts.SignupVerifier;
import com.kerosene.auth.application.service.crypto.contracts.Hasher;
import com.kerosene.auth.application.service.pow.PowService;
import com.kerosene.auth.application.service.security.profile.AccountSecurityProfileResolver;
import com.kerosene.auth.application.service.validation.totp.contracts.TOTPKeyGenerate;
import com.kerosene.auth.dto.SignupState;
import com.kerosene.auth.dto.SignupResponseDTO;
import com.kerosene.auth.dto.UserDTO;
import com.kerosene.common.infra.logging.LogSanitizer;

/** Validates a signup request and creates expiring state with TOTP material and one-time backup codes. */
@Component
public class StartSignup {

    /** Logger for signup diagnostics; user identity is logged only as a fingerprint. */
    private static final Logger log = LoggerFactory.getLogger(StartSignup.class);
    /** Number of raw backup codes generated for one enrollment. */
    private static final int BACKUP_CODE_COUNT = 10;
    /** Exclusive random bound producing zero-padded eight-digit backup codes. */
    private static final int BACKUP_CODE_BOUND = 100_000_000;
    /** Lifetime of pending signup state before it must be renewed or finalized. */
    private static final Duration SIGNUP_STATE_TTL = Duration.ofHours(24);

    /** Generates the TOTP secret and enrollment URI material. */
    private final TOTPKeyGenerate totpGenerator;
    /** Validates signup input and username availability. */
    private final SignupVerifier verifier;
    /** Stores the pending signup state and returns client setup material. */
    private final SignupStateStore stateStore;
    /** Validates proof-of-work challenges when PoW is enabled. */
    private final PowService powService;
    /** Argon2 hasher for account passphrases and backup-code verification values. */
    private final Hasher hasher;
    /** Normalizes and validates requested account-security settings. */
    private final AccountSecurityProfileResolver accountSecurityProfileResolver;
    /** Cryptographically secure source for backup-code numeric values. */
    private final SecureRandom random = new SecureRandom();

    /**
     * Creates the signup initializer with credential, PoW, TOTP, storage, and security-mode policies.
     *
     * @param totpGenerator TOTP secret generator
     * @param verifier signup input verifier
     * @param stateStore pending signup storage
     * @param powService proof-of-work verifier
     * @param accountSecurityProfileResolver requested security-mode normalizer
     * @param hasher Argon2-qualified hash implementation
     */
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

    /**
     * Validates PoW and signup data, normalizes the account, creates TOTP and backup-code material,
     * hashes secrets, clears mutable passphrase data, and stores a random 24-hour session state.
     *
     * @param dto submitted signup data; its username and passphrase may be normalized/cleared
     * @return session identifier, TOTP enrollment URI, raw backup codes, and response status
     * @throws AuthExceptions.InvalidCredentials when PoW or signup validation fails
     */
    public SignupResponseDTO execute(UserDTO dto) {
        if (!powService.isEnabled()) {
            log.warn("PoW is disabled — skipping challenge verification for signup userRef={}",
                    LogSanitizer.fingerprint(dto.getUsername()));
        } else if (!powService.verifyChallenge(dto.getChallenge(), dto.getNonce())) {
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
        state.setPassphrase(hashedPassphrase);
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

    /** Generates ten eight-digit codes and stores only their Argon2 hashes in pending signup state. */
    /** @return paired raw client codes and hashed persistence values */
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

    /**
     * Holds raw codes for one-time client disclosure alongside their stored hashes.
     * @param rawCodes codes disclosed to the signup client
     * @param hashedCodes values persisted for later verification
     */
    private record BackupCodes(List<String> rawCodes, List<String> hashedCodes) {
    }
}
