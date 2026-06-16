package source.auth.application.orchestrator.signup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import source.auth.application.orchestrator.signup.port.PasskeyGateway;
import source.auth.application.orchestrator.signup.port.SignupStateStore;
import source.auth.application.orchestrator.signup.port.UserNotifier;
import source.auth.application.service.security.CosignerSecretService;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.dto.SignupState;
import source.auth.model.entity.PasskeyCredential;
import source.auth.model.entity.UserDataBase;
import source.auth.model.enums.AccountSecurityType;
import source.notification.model.NotificationKind;
import source.notification.model.NotificationSeverity;
import source.notification.model.UserNotificationPayload;
import source.security.VaultKeyProvider;
import source.common.infra.logging.LogSanitizer;
import source.common.util.CryptoUtils;
import source.kfe.dto.KfeCreateWalletRequest;
import source.kfe.model.KfeWalletKind;
import source.kfe.service.KfeWalletService;

import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class FinalizeSignupAccount {

    private static final Logger log = LoggerFactory.getLogger(FinalizeSignupAccount.class);
    private static final String ACCOUNT_CREATED_TITLE = "Conta criada";
    private static final String ACCOUNT_CREATED_BODY =
            "Sua conta foi criada com sucesso.";
    private static final String DEFAULT_ONBOARDING_WALLET_NAME = "ACCOUNT 01";

    private final SignupStateStore stateStore;
    private final UserServiceContract userService;
    private final PasskeyGateway passkeyGateway;
    private final UserNotifier userNotifier;
    private final CosignerSecretService cosignerSecretService;
    private final VaultKeyProvider vaultKeyProvider;
    private final KfeWalletService kfeWalletService;

    public FinalizeSignupAccount(
            SignupStateStore stateStore,
            UserServiceContract userService,
            PasskeyGateway passkeyGateway,
            UserNotifier userNotifier,
            CosignerSecretService cosignerSecretService,
            VaultKeyProvider vaultKeyProvider,
            KfeWalletService kfeWalletService) {
        this.stateStore = stateStore;
        this.userService = userService;
        this.passkeyGateway = passkeyGateway;
        this.userNotifier = userNotifier;
        this.cosignerSecretService = cosignerSecretService;
        this.vaultKeyProvider = vaultKeyProvider;
        this.kfeWalletService = kfeWalletService;
    }

    @Transactional
    public UserDataBase execute(String sessionId) {
        if (!vaultKeyProvider.isReady()) {
            throw new VaultNotReadyException(
                    "O servidor ainda está inicializando a segurança criptográfica. "
                            + "Tente novamente em alguns segundos.");
        }

        SignupState state = stateStore.findSignupState(sessionId);
        if (state == null) {
            throw new IllegalStateException("Signup state not found or expired.");
        }
        if (!state.isPasskeyRegistered()) {
            throw new IllegalStateException("Passkey registration is required before finalizing signup.");
        }

        try {
            UserDataBase user = resolveUser(state, sessionId);
            ensurePasskeyPresent(state, user);
            ensureUserFinancialsReady(user, state);
            schedulePostCommitCleanup(sessionId, user.getId());
            return user;
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent signup finalization detected for sessionRef={}",
                    LogSanitizer.fingerprint(sessionId), e);
            throw e;
        }
    }

    /** Thrown when the Vault master key is not yet provisioned. */
    public static class VaultNotReadyException extends RuntimeException {
        public VaultNotReadyException(String message) {
            super(message);
        }
    }

    private UserDataBase resolveUser(SignupState state, String sessionId) {
        String normalizedUsername = state.getUsername().toLowerCase(Locale.ROOT);
        UserDataBase existingUser = userService.findByUsername(normalizedUsername);
        if (existingUser != null) {
            if (needsCosignerSecret(existingUser) && existingUser.getPlatformCosignerSecret() == null) {
                existingUser.setPlatformCosignerSecret(cosignerSecretService.generateAndEncrypt());
                existingUser = userService.createUserInDataBase(existingUser);
            }
            return existingUser;
        }

        UserDataBase user = createUserFromState(state);
        maybeAttachCosignerSecret(sessionId, user);
        user = userService.createUserInDataBase(user);
        if (user.getId() == null) {
            throw new IllegalStateException("User was persisted but ID is null.");
        }
        return user;
    }

    private UserDataBase createUserFromState(SignupState state) {
        UserDataBase user = new UserDataBase();
        user.setUsername(state.getUsername());
        user.setPasswordHash(new String(state.getPassphrase()));
        user.setTOTPSecret(state.isTotpVerified() ? state.getTotpSecret() : null);
        user.setBackupCodes(state.isTotpVerified() ? state.getBackupCodes() : Collections.emptyList());
        user.setIsActive(false);
        user.setActivatedAt(null);
        user.setAccountSecurity(state.getAccountSecurity() != null
                ? state.getAccountSecurity()
                : AccountSecurityType.STANDARD);
        user.setShamirTotalShares(state.getShamirTotalShares());
        user.setShamirThreshold(state.getShamirThreshold());
        user.setMultisigThreshold(state.getMultisigThreshold() != null ? state.getMultisigThreshold() : 2);
        return user;
    }

    private boolean needsCosignerSecret(UserDataBase user) {
        return user.getAccountSecurity() == AccountSecurityType.SHAMIR
                || user.getAccountSecurity() == AccountSecurityType.MULTISIG_2FA;
    }

    private void maybeAttachCosignerSecret(String sessionId, UserDataBase user) {
        if (needsCosignerSecret(user)) {
            user.setPlatformCosignerSecret(cosignerSecretService.generateAndEncrypt());
            log.info("[Security] Platform co-signer secret generated for sessionRef={} mode={}",
                    LogSanitizer.fingerprint(sessionId), user.getAccountSecurity());
        }
    }

    private void ensurePasskeyPresent(SignupState state, UserDataBase user) {
        List<PasskeyCredential> existingCredentials = passkeyGateway.findByUserId(user.getId());
        if (!existingCredentials.isEmpty()) {
            return;
        }

        PasskeyCredential credential = new PasskeyCredential();
        credential.setUser(user);
        credential.setDeviceName(state.getPasskeyDeviceName());
        credential.setPublicKeyCose(CryptoUtils.decodeBase64(publicKeyMaterial(state)));
        byte[] credentialId = CryptoUtils.decodeBase64(state.getPasskeyCredentialId());
        credential.setCredentialId(credentialId);
        byte[] userHandle = CryptoUtils.decodeBase64(state.getPasskeyUserHandle());
        credential.setUserHandle(userHandle != null ? userHandle : credentialId);
        credential.setRelyingPartyId(state.getPasskeyRelyingPartyId());
        credential.setOriginHost(state.getPasskeyOriginHost());
        credential.setBrand(state.getPasskeyBrand());
        credential.setModel(state.getPasskeyModel());
        credential.setSerialNumber(state.getPasskeySerialNumber());
        credential.setDeviceInstallId(state.getPasskeyDeviceInstallId());
        credential.setPlatform(state.getPasskeyPlatform());
        credential.setBrowser(state.getPasskeyBrowser());
        credential.setStatus("ACTIVE");
        passkeyGateway.save(credential);
    }

    public void ensureUserFinancialsReady(UserDataBase user, SignupState optionalState) {
        if (!kfeWalletService.listWallets(user.getId()).isEmpty() || optionalState == null) {
            return;
        }
        String initialAddress = blankToNull(optionalState.getBtcDepositAddress());
        kfeWalletService.createWallet(
                user.getId(),
                new KfeCreateWalletRequest(
                        KfeWalletKind.INTERNAL,
                        DEFAULT_ONBOARDING_WALLET_NAME,
                        null,
                        null,
                        null,
                        null,
                        initialAddress,
                        null,
                        null,
                        initialAddress != null ? "SIGNUP_STATE_DEPOSIT_ADDRESS" : null,
                        initialAddress == null));
        log.info("[Onboarding] Primary KFE wallet created for userId={}", user.getId());
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void schedulePostCommitCleanup(String sessionId, Long userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runPostCommitCleanup(sessionId, userId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runPostCommitCleanup(sessionId, userId);
            }
        });
    }

    private void runPostCommitCleanup(String sessionId, Long userId) {
        try {
            stateStore.deleteSignupState(sessionId);
        } catch (RuntimeException exception) {
            log.warn("Failed to delete signup state for sessionRef={} after commit.",
                    LogSanitizer.fingerprint(sessionId), exception);
        }

        try {
            userNotifier.notify(
                    userId,
                    UserNotificationPayload.create(
                            NotificationKind.ACCOUNT_CREATED,
                            NotificationSeverity.SUCCESS,
                            ACCOUNT_CREATED_TITLE,
                            ACCOUNT_CREATED_BODY,
                            "/home",
                            "user",
                            String.valueOf(userId),
                            Map.of("activationState", "account_created")));
        } catch (RuntimeException exception) {
            log.warn("User {} finalized but notification failed.", userId, exception);
        }
    }

    private static String publicKeyMaterial(SignupState state) {
        if (state.getPasskeyPublicKeyCose() != null) {
            return state.getPasskeyPublicKeyCose();
        }
        return state.getPasskeyPublicKey();
    }

    private static byte[] decodeBase64(String value) {
        return CryptoUtils.decodeBase64(value);
    }
}
