package source.auth.application.orchestrator.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import source.auth.AuthConstants;
import source.auth.AuthExceptions;
import source.auth.application.infra.persistance.jpa.PasskeyCredentialRepository;
import source.auth.application.infra.persistance.jpa.UserRepository;
import source.auth.application.infra.persistance.redis.contracts.RedisContract;
import source.auth.application.service.authentication.contracts.SignupVerifier;
import source.auth.application.service.cripto.contracts.Cryptography;
import source.auth.application.service.cripto.contracts.Hasher;
import source.auth.application.service.passkey.PasskeyService;
import source.auth.application.service.pow.PowService;
import source.auth.application.service.validation.totp.contratcs.TOTPKeyGenerate;
import source.auth.application.service.validation.totp.contratcs.TOTPVerifier;
import source.auth.dto.EmergencyRecoveryFinishRequest;
import source.auth.dto.EmergencyRecoveryFinishResponse;
import source.auth.dto.EmergencyRecoveryStartRequest;
import source.auth.dto.EmergencyRecoveryStartResponse;
import source.auth.dto.EmergencyRecoveryState;
import source.auth.model.entity.PasskeyCredential;
import source.auth.model.entity.UserDataBase;
import source.notification.service.NotificationService;
import source.security.VaultKeyProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class EmergencyRecoveryUseCase {

    private static final Logger log = LoggerFactory.getLogger(EmergencyRecoveryUseCase.class);

    private static final Pattern RECOVERY_CODE_PATTERN = Pattern.compile("^\\d{8}$");
    private static final int NEW_BACKUP_CODE_COUNT = 10;

    private final SignupVerifier signupVerifier;
    private final PowService powService;
    private final Hasher hasher;
    private final TOTPKeyGenerate totpGenerator;
    private final TOTPVerifier totpVerifier;
    private final PasskeyService passkeyService;
    private final UserRepository userRepository;
    private final PasskeyCredentialRepository passkeyCredentialRepository;
    private final RedisContract redisContract;
    private final Cryptography cryptography;
    private final VaultKeyProvider vaultKeyProvider;
    private final NotificationService notificationService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String dummyRecoveryHash;

    @Value("${auth.recovery.required-backup-codes:3}")
    private int requiredRecoveryCodes;

    @Value("${auth.recovery.session-ttl-minutes:10}")
    private long recoverySessionTtlMinutes;

    @Value("${auth.recovery.client-window-seconds:600}")
    private long clientWindowSeconds;

    @Value("${auth.recovery.client-max-attempts:6}")
    private long clientMaxAttempts;

    @Value("${auth.recovery.username-window-seconds:1800}")
    private long usernameWindowSeconds;

    @Value("${auth.recovery.username-max-attempts:4}")
    private long usernameMaxAttempts;

    @Value("${auth.recovery.block-seconds:1800}")
    private long recoveryBlockSeconds;

    public EmergencyRecoveryUseCase(SignupVerifier signupVerifier,
            PowService powService,
            @Qualifier("Argon2Hasher") Hasher hasher,
            TOTPKeyGenerate totpGenerator,
            TOTPVerifier totpVerifier,
            PasskeyService passkeyService,
            UserRepository userRepository,
            PasskeyCredentialRepository passkeyCredentialRepository,
            RedisContract redisContract,
            @Qualifier("aes256") Cryptography cryptography,
            VaultKeyProvider vaultKeyProvider,
            NotificationService notificationService) {
        this.signupVerifier = signupVerifier;
        this.powService = powService;
        this.hasher = hasher;
        this.totpGenerator = totpGenerator;
        this.totpVerifier = totpVerifier;
        this.passkeyService = passkeyService;
        this.userRepository = userRepository;
        this.passkeyCredentialRepository = passkeyCredentialRepository;
        this.redisContract = redisContract;
        this.cryptography = cryptography;
        this.vaultKeyProvider = vaultKeyProvider;
        this.notificationService = notificationService;
        this.dummyRecoveryHash = hasher.hash("00000000".toCharArray());
    }

    public EmergencyRecoveryStartResponse start(EmergencyRecoveryStartRequest request, String clientFingerprint) {
        validateStartRequest(request);
        enforceRecoveryRateLimit(request.getUsername(), clientFingerprint);

        if (!powService.verifyChallenge(request.getChallenge(), request.getNonce())) {
            throw new AuthExceptions.InvalidCredentials(
                    "Invalid or expired Proof of Work. Please request a new challenge and calculate the correct nonce.");
        }

        String normalizedUsername = normalizeUsername(request.getUsername());
        List<String> normalizedCodes = normalizeRecoveryCodes(request.getRecoveryCodes());
        UserDataBase user = userRepository.findByUsername(normalizedUsername);

        if (user == null || user.getBackupCodes() == null || user.getBackupCodes().size() < requiredRecoveryCodes) {
            burnRecoveryCodeChecks(normalizedCodes);
            registerRecoveryFailure(normalizedUsername, clientFingerprint);
            throw new AuthExceptions.RecoveryRejectedException(
                    "Recovery request rejected. Verify the recovery codes and retry.");
        }

        if (Boolean.TRUE.equals(hasher.verify(copyCharArray(request.getNewPassphrase()), user.getPassphrase()))) {
            throw new AuthExceptions.InvalidPassphrase(
                    "The new passphrase must be different from the current passphrase.");
        }

        List<String> matchedHashes = matchRecoveryCodes(normalizedCodes, user.getBackupCodes());
        if (matchedHashes.size() != normalizedCodes.size()) {
            registerRecoveryFailure(normalizedUsername, clientFingerprint);
            throw new AuthExceptions.RecoveryRejectedException(
                    "Recovery request rejected. Verify the recovery codes and retry.");
        }

        clearRecoveryFailures(normalizedUsername, clientFingerprint);

        String totpKey = totpGenerator.keyGenerator();
        String otpUri = String.format(
                AuthConstants.TOTP_URI_FORMAT,
                AuthConstants.APP_NAME,
                normalizedUsername,
                totpKey,
                AuthConstants.APP_NAME);
        String recoverySessionId = UUID.randomUUID().toString().replace("-", "");
        String passkeyChallenge = generateRecoveryChallenge();

        EmergencyRecoveryState state = new EmergencyRecoveryState();
        state.setSessionId(recoverySessionId);
        state.setUsername(normalizedUsername);
        state.setHashedPassphrase(hasher.hash(request.getNewPassphrase()));
        state.setEncryptedTotpSecret(encryptTotpSecret(totpKey));
        state.setPasskeyChallenge(passkeyChallenge);
        state.setMatchedBackupCodeHashes(matchedHashes);

        redisContract.saveEmergencyRecoveryState(recoverySessionId, state, recoverySessionTtlMinutes);
        log.warn("[Recovery] Emergency recovery initiated for username={} using {} recovery codes.",
                normalizedUsername, matchedHashes.size());

        return new EmergencyRecoveryStartResponse(
                recoverySessionId,
                otpUri,
                passkeyChallenge,
                recoverySessionTtlMinutes * 60L,
                requiredRecoveryCodes);
    }

    @Transactional
    public EmergencyRecoveryFinishResponse finish(EmergencyRecoveryFinishRequest request) {
        validateFinishRequest(request);

        EmergencyRecoveryState state = redisContract.getdelEmergencyRecoveryState(request.getRecoverySessionId());
        if (state == null) {
            throw new AuthExceptions.RecoverySessionExpiredException(
                    "Recovery session expired or was already consumed. Restart the recovery flow.");
        }

        UserDataBase user = userRepository.findByUsername(state.getUsername());
        if (user == null) {
            throw new AuthExceptions.RecoveryRejectedException("Recovery request rejected.");
        }

        if (user.getBackupCodes() == null
                || state.getMatchedBackupCodeHashes() == null
                || !user.getBackupCodes().containsAll(state.getMatchedBackupCodeHashes())) {
            throw new AuthExceptions.RecoveryRejectedException(
                    "Recovery request rejected. Existing recovery codes were already rotated.");
        }

        String totpSecret = decryptTotpSecret(state.getEncryptedTotpSecret());
        if (!totpVerifier.totpMatcher(totpSecret, request.getTotpCode())) {
            throw new AuthExceptions.RecoveryRejectedException(
                    "Recovery request rejected. The new authenticator proof was invalid.");
        }

        byte[] publicKeyBytes = decodePasskeyPublicKey(request);
        if (!passkeyService.verifySignature(
                state.getUsername(),
                state.getPasskeyChallenge(),
                request.getSignature(),
                publicKeyBytes,
                request.getAuthData(),
                request.getClientDataJSON())) {
            throw new AuthExceptions.RecoveryRejectedException(
                    "Recovery request rejected. The new passkey proof was invalid.");
        }

        user.setPassphrase(state.getHashedPassphrase());
        user.setTOTPSecret(totpSecret);
        user.setFailedLoginAttempts(0);

        GeneratedBackupCodes newBackupCodes = generateNewBackupCodes();
        user.setBackupCodes(newBackupCodes.hashedCodes());
        userRepository.save(user);

        List<PasskeyCredential> existingCredentials = passkeyCredentialRepository.findByUserId(user.getId());
        if (!existingCredentials.isEmpty()) {
            passkeyCredentialRepository.deleteAll(existingCredentials);
        }
        passkeyCredentialRepository.save(buildPasskeyCredential(user, request, publicKeyBytes));

        notificationService.notifyUser(
                user.getId(),
                "Emergency recovery completed",
                "Your passphrase, TOTP, passkey and recovery codes were rotated. Login again with the new credentials.");

        log.warn("[Recovery] Emergency recovery finished for username={}. Old credentials rotated.", user.getUsername());
        return new EmergencyRecoveryFinishResponse(user.getUsername(), newBackupCodes.rawCodes());
    }

    private void validateStartRequest(EmergencyRecoveryStartRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Recovery request body is required.");
        }

        String normalizedUsername = normalizeUsername(request.getUsername());
        signupVerifier.checkUsernameNotNull(normalizedUsername);
        signupVerifier.checkUsernameFormat(normalizedUsername);
        signupVerifier.checkUsernameLength(normalizedUsername);
        signupVerifier.checkPassphraseNotNull(request.getNewPassphrase());
        signupVerifier.checkPassphraseLength(request.getNewPassphrase());
        signupVerifier.checkPassphraseBip39(copyCharArray(request.getNewPassphrase()));

        List<String> normalizedCodes = normalizeRecoveryCodes(request.getRecoveryCodes());
        if (normalizedCodes.size() < requiredRecoveryCodes) {
            throw new IllegalArgumentException(
                    "At least " + requiredRecoveryCodes + " distinct recovery codes are required.");
        }

        if (request.getChallenge() == null || request.getChallenge().isBlank()
                || request.getNonce() == null || request.getNonce().isBlank()) {
            throw new IllegalArgumentException("Proof of Work challenge and nonce are required.");
        }
    }

    private void validateFinishRequest(EmergencyRecoveryFinishRequest request) {
        if (request == null || request.getRecoverySessionId() == null || request.getRecoverySessionId().isBlank()) {
            throw new IllegalArgumentException("Recovery sessionId is required.");
        }
        if (request.getTotpCode() == null || request.getTotpCode().isBlank()) {
            throw new IllegalArgumentException("A fresh TOTP code from the new authenticator is required.");
        }
        if (request.getSignature() == null || request.getSignature().isBlank()
                || request.getAuthData() == null || request.getAuthData().isBlank()
                || request.getClientDataJSON() == null || request.getClientDataJSON().isBlank()
                || request.getCredentialId() == null || request.getCredentialId().isBlank()
                || request.getDeviceName() == null || request.getDeviceName().isBlank()) {
            throw new IllegalArgumentException("A new passkey proof is required to complete recovery.");
        }
    }

    private void enforceRecoveryRateLimit(String username, String clientFingerprint) {
        String normalizedUsername = normalizeUsername(username);
        String clientKey = "auth:recovery:attempts:client:" + clientFingerprint;
        String clientBlockKey = "auth:recovery:block:client:" + clientFingerprint;
        String userBlockKey = "auth:recovery:block:user:" + normalizedUsername;

        if (redisContract.getValue(clientBlockKey) != null || redisContract.getValue(userBlockKey) != null) {
            throw new AuthExceptions.RecoveryRateLimitedException(
                    "Emergency recovery is temporarily blocked for this client or username.");
        }

        Long clientAttempts = redisContract.increment(clientKey);
        if (clientAttempts == 1L) {
            redisContract.expire(clientKey, clientWindowSeconds);
        }
        if (clientAttempts > clientMaxAttempts) {
            redisContract.setValue(clientBlockKey, "1", recoveryBlockSeconds);
            throw new AuthExceptions.RecoveryRateLimitedException(
                    "Emergency recovery is temporarily blocked for this client.");
        }
    }

    private void registerRecoveryFailure(String normalizedUsername, String clientFingerprint) {
        String userAttemptsKey = "auth:recovery:attempts:user:" + normalizedUsername;
        Long userAttempts = redisContract.increment(userAttemptsKey);
        if (userAttempts == 1L) {
            redisContract.expire(userAttemptsKey, usernameWindowSeconds);
        }
        if (userAttempts >= usernameMaxAttempts) {
            redisContract.setValue("auth:recovery:block:user:" + normalizedUsername, "1", recoveryBlockSeconds);
            redisContract.setValue("auth:recovery:block:client:" + clientFingerprint, "1", recoveryBlockSeconds);
        }
    }

    private void clearRecoveryFailures(String normalizedUsername, String clientFingerprint) {
        redisContract.deleteValue("auth:recovery:attempts:user:" + normalizedUsername);
        redisContract.deleteValue("auth:recovery:block:user:" + normalizedUsername);
        redisContract.deleteValue("auth:recovery:attempts:client:" + clientFingerprint);
        redisContract.deleteValue("auth:recovery:block:client:" + clientFingerprint);
    }

    private List<String> normalizeRecoveryCodes(List<String> recoveryCodes) {
        if (recoveryCodes == null) {
            throw new IllegalArgumentException("Recovery codes are required.");
        }

        Set<String> distinctCodes = new LinkedHashSet<>();
        for (String rawCode : recoveryCodes) {
            if (rawCode == null) {
                throw new IllegalArgumentException("Recovery codes cannot contain null values.");
            }
            String normalized = rawCode.trim();
            if (!RECOVERY_CODE_PATTERN.matcher(normalized).matches()) {
                throw new IllegalArgumentException("Recovery codes must be 8 numeric digits.");
            }
            distinctCodes.add(normalized);
        }
        return new ArrayList<>(distinctCodes);
    }

    private List<String> matchRecoveryCodes(List<String> submittedCodes, List<String> storedHashes) {
        List<String> matchedHashes = new ArrayList<>();
        boolean[] consumed = new boolean[storedHashes.size()];

        for (String code : submittedCodes) {
            boolean matched = false;
            for (int i = 0; i < storedHashes.size(); i++) {
                if (consumed[i]) {
                    continue;
                }
                if (Boolean.TRUE.equals(hasher.verify(code.toCharArray(), storedHashes.get(i)))) {
                    consumed[i] = true;
                    matchedHashes.add(storedHashes.get(i));
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return List.of();
            }
        }

        return matchedHashes;
    }

    private void burnRecoveryCodeChecks(List<String> submittedCodes) {
        for (String code : submittedCodes) {
            hasher.verify(code.toCharArray(), dummyRecoveryHash);
        }
    }

    private GeneratedBackupCodes generateNewBackupCodes() {
        List<String> rawCodes = new ArrayList<>(NEW_BACKUP_CODE_COUNT);
        List<String> hashedCodes = new ArrayList<>(NEW_BACKUP_CODE_COUNT);

        for (int i = 0; i < NEW_BACKUP_CODE_COUNT; i++) {
            String code = String.format("%08d", secureRandom.nextInt(100000000));
            rawCodes.add(code);
            hashedCodes.add(hasher.hash(code.toCharArray()));
        }

        return new GeneratedBackupCodes(rawCodes, hashedCodes);
    }

    private PasskeyCredential buildPasskeyCredential(UserDataBase user, EmergencyRecoveryFinishRequest request,
            byte[] publicKeyBytes) {
        PasskeyCredential credential = new PasskeyCredential();
        credential.setUser(user);
        credential.setDeviceName(request.getDeviceName());
        credential.setPublicKeyCose(publicKeyBytes);

        Base64.Decoder decoder = Base64.getDecoder();
        try {
            credential.setCredentialId(decoder.decode(request.getCredentialId()));
            if (request.getUserHandle() != null && !request.getUserHandle().isBlank()) {
                credential.setUserHandle(decoder.decode(request.getUserHandle()));
            }
        } catch (IllegalArgumentException e) {
            decoder = Base64.getUrlDecoder();
            credential.setCredentialId(decoder.decode(request.getCredentialId()));
            if (request.getUserHandle() != null && !request.getUserHandle().isBlank()) {
                credential.setUserHandle(decoder.decode(request.getUserHandle()));
            }
        }

        return credential;
    }

    private byte[] decodePasskeyPublicKey(EmergencyRecoveryFinishRequest request) {
        String keyToDecode = request.getPublicKeyCose() != null && !request.getPublicKeyCose().isBlank()
                ? request.getPublicKeyCose()
                : request.getPublicKey();
        if (keyToDecode == null || keyToDecode.isBlank()) {
            throw new IllegalArgumentException("publicKeyCose or publicKey is required.");
        }

        try {
            return Base64.getDecoder().decode(keyToDecode);
        } catch (IllegalArgumentException e) {
            return Base64.getUrlDecoder().decode(keyToDecode);
        }
    }

    private String encryptTotpSecret(String totpSecret) {
        try {
            byte[] encrypted = cryptography.encrypt(totpSecret.getBytes(StandardCharsets.UTF_8),
                    vaultKeyProvider.getMasterKey());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to protect the recovery TOTP seed in Redis.", e);
        }
    }

    private String decryptTotpSecret(String encryptedTotpSecret) {
        try {
            byte[] decrypted = cryptography.decrypt(Base64.getDecoder().decode(encryptedTotpSecret),
                    vaultKeyProvider.getMasterKey());
            try {
                return new String(decrypted, StandardCharsets.UTF_8);
            } finally {
                java.util.Arrays.fill(decrypted, (byte) 0);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to recover the protected TOTP seed.", e);
        }
    }

    private String generateRecoveryChallenge() {
        byte[] challenge = new byte[32];
        secureRandom.nextBytes(challenge);
        StringBuilder sb = new StringBuilder();
        for (byte b : challenge) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase();
    }

    private char[] copyCharArray(char[] input) {
        if (input == null) {
            return null;
        }
        char[] copy = new char[input.length];
        System.arraycopy(input, 0, copy, 0, input.length);
        return copy;
    }

    public static String buildClientFingerprint(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String clientIp = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        String raw = clientIp + "|" + (userAgent == null ? "-" : userAgent);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            return raw.replaceAll("[^a-zA-Z0-9_.:-]", "_");
        }
    }

    private record GeneratedBackupCodes(List<String> rawCodes, List<String> hashedCodes) {
    }
}
