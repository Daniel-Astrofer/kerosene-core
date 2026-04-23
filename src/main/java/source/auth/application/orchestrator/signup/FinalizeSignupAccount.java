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
import source.common.util.CryptoUtils;
import source.wallet.application.port.in.CreateWalletUseCase;
import source.wallet.dto.WalletRequestDTO;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletContract;
import source.ledger.service.LedgerContract;
import source.ledger.exceptions.LedgerExceptions;

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

    private final SignupStateStore stateStore;
    private final UserServiceContract userService;
    private final PasskeyGateway passkeyGateway;
    private final UserNotifier userNotifier;
    private final CosignerSecretService cosignerSecretService;
    private final VaultKeyProvider vaultKeyProvider;
    private final CreateWalletUseCase walletUseCase;
    private final WalletContract walletContract;
    private final LedgerContract ledgerContract;

    public FinalizeSignupAccount(
            SignupStateStore stateStore,
            UserServiceContract userService,
            PasskeyGateway passkeyGateway,
            UserNotifier userNotifier,
            CosignerSecretService cosignerSecretService,
            VaultKeyProvider vaultKeyProvider,
            CreateWalletUseCase walletUseCase,
            WalletContract walletContract,
            LedgerContract ledgerContract) {
        this.stateStore = stateStore;
        this.userService = userService;
        this.passkeyGateway = passkeyGateway;
        this.userNotifier = userNotifier;
        this.cosignerSecretService = cosignerSecretService;
        this.vaultKeyProvider = vaultKeyProvider;
        this.walletUseCase = walletUseCase;
        this.walletContract = walletContract;
        this.ledgerContract = ledgerContract;
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
            log.warn("Concurrent signup finalization detected for session {}", sessionId, e);
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
            log.info("[Security] Platform co-signer secret generated for session {} mode {}",
                    sessionId, user.getAccountSecurity());
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
        credential.setCredentialId(CryptoUtils.decodeBase64(state.getPasskeyCredentialId()));
        credential.setUserHandle(CryptoUtils.decodeBase64(state.getPasskeyUserHandle()));
        passkeyGateway.save(credential);
    }

    public void ensureUserFinancialsReady(UserDataBase user, SignupState optionalState) {
        List<WalletEntity> wallets = walletContract.findByUserId(user.getId());
        if (wallets.isEmpty() && optionalState != null) {
            String passphrase = optionalState.getPassphrase() != null ? new String(optionalState.getPassphrase()) : "PASSKEY_SECURED";
            WalletRequestDTO request = new WalletRequestDTO(passphrase, "ACCOUNT 01", null);
            walletUseCase.createWallet(request, user.getId());
            log.info("[Onboarding] Primary wallet created for user {}", user.getUsername());
        } else {
            for (WalletEntity wallet : wallets) {
                try {
                    if (!ledgerContract.existsByWalletId(wallet.getId())) {
                        ledgerContract.createLedger(wallet, "Automated healing for missing ledger");
                        log.info("[Onboarding] Healed missing ledger for wallet {} user {}", wallet.getName(), user.getUsername());
                    }
                } catch (LedgerExceptions.LedgerAlreadyExistsException ignored) {
                    // Already has a ledger, that's fine.
                } catch (Exception e) {
                    log.error("[Onboarding] Failed to heal ledger for wallet {} user {}", wallet.getName(), user.getUsername(), e);
                }
            }
        }
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
            log.warn("Failed to delete signup state for session {} after commit.", sessionId, exception);
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
