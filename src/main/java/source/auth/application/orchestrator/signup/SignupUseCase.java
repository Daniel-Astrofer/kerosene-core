package source.auth.application.orchestrator.signup;

import org.checkerframework.checker.nullness.qual.NonNull;
import source.auth.AuthConstants;
import source.auth.AuthExceptions;
import source.auth.application.infra.persistance.redis.contracts.RedisContract;
import source.auth.application.orchestrator.login.contracts.Signup;
import source.auth.application.service.authentication.contracts.SignupVerifier;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.auth.application.service.security.CosignerSecretService;
import source.auth.application.service.validation.totp.contratcs.TOTPKeyGenerate;
import source.auth.application.service.validation.totp.contratcs.TOTPVerifier;
import source.auth.dto.UserDTO;
import source.auth.dto.SignupState;
import source.auth.dto.SignupResponseDTO;
import source.auth.model.entity.UserDataBase;
import source.auth.model.entity.PasskeyCredential;
import source.auth.model.enums.AccountSecurityType;
import source.voucher.service.VoucherService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

/**
 * Use case orchestrator for user signup process.
 * Handles TOTP generation, user validation, and creating the temporary Redis
 * SignupState.
 */
@Component
public class SignupUseCase implements Signup {

    private static final Logger log = LoggerFactory.getLogger(SignupUseCase.class);

    private final TOTPKeyGenerate totpGenerator;
    private final TOTPVerifier totpVerifier;
    private final SignupVerifier verifier;
    private final RedisServicer cache;
    private final RedisContract redisContract;
    private final VoucherService voucherService;
    private final source.auth.application.service.pow.PowService powService;
    private final source.auth.application.service.user.contract.UserServiceContract userService;
    private final source.auth.application.infra.persistance.jpa.PasskeyCredentialRepository passkeyRepo;
    private final source.notification.service.NotificationService notificationService;
    private final CosignerSecretService cosignerSecretService;
    private final source.auth.application.service.cripto.contracts.Hasher hasher;

    public SignupUseCase(TOTPKeyGenerate totpGenerator,
            TOTPVerifier totpVerifier,
            SignupVerifier verifier,
            RedisServicer cache,
            RedisContract redisContract,
            VoucherService voucherService,
            source.auth.application.service.pow.PowService powService,
            source.auth.application.service.user.contract.UserServiceContract userService,
            source.auth.application.infra.persistance.jpa.PasskeyCredentialRepository passkeyRepo,
            source.notification.service.NotificationService notificationService,
            CosignerSecretService cosignerSecretService,
            @org.springframework.beans.factory.annotation.Qualifier("Argon2Hasher") source.auth.application.service.cripto.contracts.Hasher hasher) {
        this.totpGenerator = totpGenerator;
        this.totpVerifier = totpVerifier;
        this.verifier = verifier;
        this.cache = cache;
        this.redisContract = redisContract;
        this.voucherService = voucherService;
        this.powService = powService;
        this.userService = userService;
        this.passkeyRepo = passkeyRepo;
        this.notificationService = notificationService;
        this.cosignerSecretService = cosignerSecretService;
        this.hasher = hasher;
    }

    /**
     * Initiates the signup process by validating user credentials and generating
     * TOTP.
     *
     * @param dto the user data transfer object containing username and passphrase
     * @return TOTP URI for QR code generation
     */
    @Override
    public SignupResponseDTO signupUser(UserDTO dto) {
        if (!powService.verifyChallenge(dto.getChallenge(), dto.getNonce())) {
            throw new AuthExceptions.InvalidCredentials(
                    "Invalid or expired Proof of Work. Please request a new challenge and calculate the correct nonce.");
        }

        String normalizedUsername = dto.getUsername().toLowerCase();
        dto.setUsername(normalizedUsername);

        verifier.verify(dto.getUsername(), dto.getPassphrase());

        String totpKey = totpGenerator.keyGenerator();
        String otpUri = String.format(
                AuthConstants.TOTP_URI_FORMAT,
                AuthConstants.APP_NAME,
                dto.getUsername(),
                totpKey,
                AuthConstants.APP_NAME);

        java.security.SecureRandom random = new java.security.SecureRandom();
        java.util.List<String> rawCodes = new java.util.ArrayList<>();
        java.util.List<String> hashedCodes = new java.util.ArrayList<>();

        for (int i = 0; i < 10; i++) {
            String code = String.format("%08d", random.nextInt(100000000));
            rawCodes.add(code);
            hashedCodes.add(hasher.hash(code.toCharArray()));
        }

        // Hash passphrase BEFORE storing in Redis (fix: plaintext passphrase in Redis)
        String hashedPassphrase = hasher.hash(dto.getPassphrase());
        java.util.Arrays.fill(dto.getPassphrase(), '\0');
        // Store hashed passphrase as a char array in the DTO for Redis
        dto.setPassphrase(hashedPassphrase.toCharArray());

        dto.setTotpSecret(totpKey);
        dto.setBackupCodes(hashedCodes);
        cache.createTempUser(dto);

        return new SignupResponseDTO(otpUri, rawCodes);
    }

    /**
     * Completes the signup process by verifying TOTP and creating the Redis
     * SignupState.
     * Database creation is deferred until 3 Bitcoin Confirmations.
     *
     * @param dto the user data transfer object containing TOTP code
     * @return The sessionId to track the onboarding session
     */
    @Override
    public String createUser(UserDTO dto) {
        UserDTO cachedUser = cache.getFromRedis(dto);

        if (cachedUser == null) {
            throw new AuthExceptions.TotpTimeExceededException(AuthConstants.ERR_TOTP_EXPIRED);
        }

        // 1. Verify the TOTP code the user submitted against the secret stored in Redis
        if (dto.getTotpCode() == null || dto.getTotpCode().isEmpty()) {
            throw new AuthExceptions.InvalidCredentials("TOTP code required to complete registration.");
        }
        totpVerifier.totpVerify(cachedUser.getTotpSecret(), dto.getTotpCode());

        // 2. Generate a secure session ID for the onboarding process
        String sessionId = UUID.randomUUID().toString().replace("-", "");

        // 3. Hydrate the SignupState object
        SignupState state = getSignupState(sessionId, cachedUser);

        // 4. Store the state in Redis for 24 hours (1440 minutes)
        redisContract.saveSignupState(sessionId, state, 1440);

        cache.deleteFromRedis(cachedUser);

        return sessionId;
    }

    private static @NonNull SignupState getSignupState(String sessionId, UserDTO cachedUser) {
        SignupState state = new SignupState();
        state.setSessionId(sessionId);
        state.setUsername(cachedUser.getUsername());
        state.setPassphrase(cachedUser.getPassphrase());
        state.setTotpSecret(cachedUser.getTotpSecret());
        state.setTotpVerified(true);
        state.setPasskeyRegistered(false);
        state.setPaymentConfirmed(false);
        // Propagate chosen security mode (defaults to STANDARD if not set)
        AccountSecurityType secMode = cachedUser.getAccountSecurity() != null
                ? cachedUser.getAccountSecurity()
                : AccountSecurityType.STANDARD;
        state.setAccountSecurity(secMode);

        state.setBackupCodes(cachedUser.getBackupCodes());
        return state;
    }

    public void finalizeUserFromRedis(String sessionId, String txid, BigDecimal amountPaid) {
        // Validate payment amount before doing anything
        if (amountPaid == null || amountPaid.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("finalizeUserFromRedis: invalid amountPaid {} for session {}", amountPaid, sessionId);
            throw new IllegalArgumentException("amountPaid deve ser maior que zero.");
        }

        // Atomic GETDEL: fetch + delete in one Redis operation, eliminating TOCTOU race
        // condition
        SignupState state = redisContract.getdelSignupState(sessionId);
        if (state == null) {
            log.warn(
                    "finalizeUserFromRedis: SignupState for session {} not found — already finalized or expired. Skipping.",
                    sessionId);
            return;
        }

        if (!state.isTotpVerified()) {
            log.warn("SignupState for session {} lacks TOTP verification.", sessionId);
            return;
        }

        try {
            // 1. Create native UserDataBase
            UserDataBase user = new UserDataBase();
            user.setUsername(state.getUsername());
            // Passphrase is already hashed (done at signup time)
            user.setPassphrase(new String(state.getPassphrase()));
            user.setTOTPSecret(state.getTotpSecret());
            user.setIsActive(false); // will be true after voucher claim
            user.setAccountSecurity(state.getAccountSecurity() != null
                    ? state.getAccountSecurity()
                    : AccountSecurityType.STANDARD);
            user.setBackupCodes(state.getBackupCodes());

            // For co-signer modes: generate & store an encrypted platform secret
            if (user.getAccountSecurity() == AccountSecurityType.SHAMIR
                    || user.getAccountSecurity() == AccountSecurityType.MULTISIG_2FA) {
                String encryptedSecret = cosignerSecretService.generateAndEncrypt();
                user.setPlatformCosignerSecret(encryptedSecret);
                log.info("[Security] Platform co-signer secret generated for session {} mode {}",
                        sessionId, user.getAccountSecurity());
            }

            // Persist User and capture the returned entity with its generated ID
            user = userService.createUserInDataBase(user);
            if (user.getId() == null) {
                throw new IllegalStateException("User was persisted but ID is null — aborting finalize.");
            }

            // 2. Deserialize PasskeyCredential if user registered one
            if (state.isPasskeyRegistered() && state.getPasskeyCredentialJson() != null) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                PasskeyCredential cred = mapper.readValue(state.getPasskeyCredentialJson(), PasskeyCredential.class);
                cred.setUser(user);
                passkeyRepo.save(cred);
            }

            // 3. Create and Claim the onboarding voucher, giving them isActive = true
            voucherService.createAndClaimOnboardingVoucher(user.getId(), txid, amountPaid);

            // 4. Notify the user via Websocket/Push
            notificationService.notifyUser(user.getId(), "Account Created!",
                    "Your onboarding payment reached 3 confirmations. Your account is now active.");

            log.info("Successfully finalized user {} via onboarding session {}", user.getUsername(), sessionId);

        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Extreme edge case: two distributed nodes both read Redis before either
            // finished deleting it. User was already created — safe to ignore.
            log.warn(
                    "finalizeUserFromRedis: duplicate insert for session {} — concurrent request already created user.",
                    sessionId);
        } catch (Exception e) {
            log.error("Failed to finalize user database insertion for session {}", sessionId, e);
        }
    }
}
