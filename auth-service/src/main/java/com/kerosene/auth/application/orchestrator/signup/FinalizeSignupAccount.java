package com.kerosene.auth.application.orchestrator.signup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.kerosene.common.financial.operations.FinancialWalletProvisioningPort;
import com.kerosene.auth.application.orchestrator.signup.port.PasskeyGateway;
import com.kerosene.auth.application.orchestrator.signup.port.SignupStateStore;
import com.kerosene.auth.application.orchestrator.signup.port.UserNotifier;
import com.kerosene.auth.application.service.security.CosignerSecretService;
import com.kerosene.auth.application.service.user.contract.UserServiceContract;
import com.kerosene.auth.dto.SignupState;
import com.kerosene.auth.model.entity.PasskeyCredential;
import com.kerosene.auth.model.entity.UserDataBase;
import com.kerosene.auth.model.enums.AccountSecurityType;
import com.kerosene.notification.l10n.NotificationMessageKey;
import com.kerosene.notification.l10n.NotificationMessages;
import com.kerosene.notification.model.NotificationKind;
import com.kerosene.notification.model.NotificationSeverity;
import com.kerosene.security.infra.VaultKeyProvider;
import com.kerosene.common.infra.logging.LogSanitizer;
import com.kerosene.platform.util.CryptoUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Finalizes a validated signup session into an active user and schedules external post-commit work. */
@Component
public class FinalizeSignupAccount {

    /** Logger used for sanitized operational diagnostics around transactional finalization. */
    private static final Logger log = LoggerFactory.getLogger(FinalizeSignupAccount.class);

    /** Port for reading signup state and scheduling its cleanup after commit. */
    private final SignupStateStore stateStore;
    /** User persistence and lookup boundary. */
    private final UserServiceContract userService;
    /** Port for persisting signup-provided WebAuthn credentials. */
    private final PasskeyGateway passkeyGateway;
    /** Post-commit account-created notification boundary. */
    private final UserNotifier userNotifier;
    /** Generates and encrypts platform co-signer material for advanced account modes. */
    private final CosignerSecretService cosignerSecretService;
    /** Reports whether cryptographic key material is ready for account provisioning. */
    private final VaultKeyProvider vaultKeyProvider;
    /** Ensures the account's primary wallet exists after the database transaction commits. */
    private final FinancialWalletProvisioningPort financialWalletProvisioningPort;

    /**
     * Creates the finalization operation with persistence, crypto readiness, and post-commit ports.
     *
     * @param stateStore pending signup state port
     * @param userService user persistence boundary
     * @param passkeyGateway passkey credential persistence port
     * @param userNotifier account-created notification port
     * @param cosignerSecretService co-signer secret generator and encryptor
     * @param vaultKeyProvider cryptographic readiness provider
     * @param financialWalletProvisioningPort primary wallet provisioning boundary
     */
    public FinalizeSignupAccount(
            SignupStateStore stateStore,
            UserServiceContract userService,
            PasskeyGateway passkeyGateway,
            UserNotifier userNotifier,
            CosignerSecretService cosignerSecretService,
            VaultKeyProvider vaultKeyProvider,
            FinancialWalletProvisioningPort financialWalletProvisioningPort) {
        this.stateStore = stateStore;
        this.userService = userService;
        this.passkeyGateway = passkeyGateway;
        this.userNotifier = userNotifier;
        this.cosignerSecretService = cosignerSecretService;
        this.vaultKeyProvider = vaultKeyProvider;
        this.financialWalletProvisioningPort = financialWalletProvisioningPort;
    }

    /**
     * Validates crypto readiness and signup state, resolves or creates the user, ensures a passkey
     * and financial prerequisites, activates the account, then schedules cleanup and notification.
     *
     * @param sessionId signup session identifier
     * @return persisted and active user account
     * @throws VaultNotReadyException when cryptographic keys are not initialized
     * @throws IllegalStateException when signup state is missing or passkey registration is incomplete
     * @throws DataIntegrityViolationException when concurrent finalization collides at persistence
     */
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
            user = activateFinalizedUser(user);
            schedulePostCommitCleanup(sessionId, user.getId());
            return user;
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent signup finalization detected for sessionRef={}",
                    LogSanitizer.fingerprint(sessionId), e);
            throw e;
        } catch (RuntimeException e) {
            log.error("[FINALIZE] Failed to finalize signup for sessionRef={} userRef={}: {} - {}",
                    LogSanitizer.fingerprint(sessionId),
                    LogSanitizer.fingerprint(state.getUsername()),
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw e;
        }
    }

    /** Thrown when the Vault master key is not yet provisioned. */
    /** Signals that signup finalization cannot proceed because cryptographic keys are not ready. */
    public static class VaultNotReadyException extends RuntimeException {
        /** Creates the readiness failure with a caller-facing message. */
        /** @param message explanation of why finalization must be retried */
        public VaultNotReadyException(String message) {
            super(message);
        }
    }

    /** Finds the normalized account or creates it from signup state, repairing required co-signer material. */
    /** @param state validated, persisted signup workflow state */
    /** @param sessionId signup session used only for sanitized diagnostics */
    /** @return persisted account entity */
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

    /** Marks a not-yet-active account active and records its activation timestamp. */
    /** @param user persisted account to activate */
    /** @return active persisted account */
    private UserDataBase activateFinalizedUser(UserDataBase user) {
        if (Boolean.TRUE.equals(user.getIsActive())) {
            return user;
        }
        user.setIsActive(true);
        user.setActivatedAt(LocalDateTime.now());
        return userService.createUserInDataBase(user);
    }

    /** Maps pending signup credentials and security settings into an inactive user entity. */
    /** @param state pending signup values */
    /** @return user entity ready for persistence */
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

    /** Returns whether the selected account mode requires platform co-signer secret material. */
    /** @param user account whose security mode is checked */
    /** @return true for SHAMIR or MULTISIG_2FA */
    private boolean needsCosignerSecret(UserDataBase user) {
        return user.getAccountSecurity() == AccountSecurityType.SHAMIR
                || user.getAccountSecurity() == AccountSecurityType.MULTISIG_2FA;
    }

    /** Generates and attaches encrypted co-signer material only for modes that require it. */
    /** @param sessionId signup session used for sanitized logs */
    /** @param user account entity receiving the encrypted secret */
    private void maybeAttachCosignerSecret(String sessionId, UserDataBase user) {
        if (needsCosignerSecret(user)) {
            user.setPlatformCosignerSecret(cosignerSecretService.generateAndEncrypt());
            log.info("[Security] Platform co-signer secret generated for sessionRef={} mode={}",
                    LogSanitizer.fingerprint(sessionId), user.getAccountSecurity());
        }
    }

    /**
     * Ensures at least one credential exists. When signup state has complete WebAuthn material,
     * decodes and persists that credential; device-key-only onboarding does not synthesize an
     * empty WebAuthn row.
     *
     * @param state pending signup state carrying optional passkey material
     * @param user persisted owner of the credential
     */
    private void ensurePasskeyPresent(SignupState state, UserDataBase user) {
        List<PasskeyCredential> existingCredentials = passkeyGateway.findByUserId(user.getId());
        if (!existingCredentials.isEmpty()) {
            return;
        }

        // Device-key onboarding finalizes signup with passkeyRegistered=true but without
        // WebAuthn passkey material. Skip empty inserts that violate passkey_credentials NN.
        String publicKey = publicKeyMaterial(state);
        if (state.getPasskeyCredentialId() == null || state.getPasskeyCredentialId().isBlank()
                || publicKey == null || publicKey.isBlank()) {
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

    /**
     * Schedules primary wallet readiness after commit when synchronization is active, or runs it
     * immediately when invoked outside a transaction.
     *
     * @param user account whose primary wallet must be ready
     * @param optionalState signup state that may carry an initial BTC deposit address
     */
    public void ensureUserFinancialsReady(UserDataBase user, SignupState optionalState) {
        Long userId = user.getId();
        String initialAddress = optionalState != null ? optionalState.getBtcDepositAddress() : null;
        runAfterCommit(() -> financialWalletProvisioningPort.ensurePrimaryWalletReady(userId, initialAddress));
    }

    /** Schedules state deletion and account-created notification after the user transaction commits. */
    /** @param sessionId signup state key to delete after commit */
    /** @param userId persisted account identifier for notification */
    private void schedulePostCommitCleanup(String sessionId, Long userId) {
        runAfterCommit(() -> runPostCommitCleanup(sessionId, userId));
    }

    /** Registers a transaction synchronization or immediately runs the task without an active synchronization. */
    /** @param task external or cleanup action to execute after commit */
    private void runAfterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new SignupAfterCommitTask(task));
    }

    /** Transaction callback that runs one signup side effect only after a successful commit. */
    private static final class SignupAfterCommitTask implements TransactionSynchronization {
        /** Deferred work to execute after the database transaction commits. */
        private final Runnable task;

        /** Captures the post-commit action. */
        /** @param task deferred action to execute after commit */
        private SignupAfterCommitTask(Runnable task) {
            this.task = task;
        }

        /** Runs the captured action after the surrounding transaction commits successfully. */
        @Override
        public void afterCommit() {
            task.run();
        }
    }

    /** Deletes consumed signup state and sends an account-created notification, isolating each failure. */
    /** @param sessionId signup state key */
    /** @param userId persisted account identifier */
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
                    NotificationMessages.payload(
                            NotificationKind.ACCOUNT_CREATED,
                            NotificationSeverity.SUCCESS,
                            NotificationMessageKey.ACCOUNT_CREATED,
                            "/home",
                            "user",
                            String.valueOf(userId),
                            Map.of("activationState", "account_created")));
        } catch (RuntimeException exception) {
            log.warn("User {} finalized but notification failed.", userId, exception);
        }
    }

    /** Selects COSE public-key material when available, otherwise returns the legacy key field. */
    /** @param state pending signup state */
    /** @return encoded COSE or legacy public key, possibly {@code null} */
    private static String publicKeyMaterial(SignupState state) {
        if (state.getPasskeyPublicKeyCose() != null) {
            return state.getPasskeyPublicKeyCose();
        }
        return state.getPasskeyPublicKey();
    }

    /** Decodes a Base64 value using the shared platform crypto utility. */
    /** @param value encoded bytes */
    /** @return decoded bytes or {@code null} according to the shared decoder contract */
    private static byte[] decodeBase64(String value) {
        return CryptoUtils.decodeBase64(value);
    }
}
