package source.auth.application.orchestrator.signup;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import source.auth.application.orchestrator.signup.port.OnboardingVoucherPort;
import source.auth.application.orchestrator.signup.port.PasskeyGateway;
import source.auth.application.orchestrator.signup.port.SignupStateStore;
import source.auth.application.orchestrator.signup.port.UserNotifier;
import source.auth.application.service.security.CosignerSecretService;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.dto.SignupState;
import source.auth.model.entity.PasskeyCredential;
import source.auth.model.entity.UserDataBase;
import source.auth.model.enums.AccountSecurityType;

@Component
public class FinalizeSignupOnPayment {

    private static final Logger log = LoggerFactory.getLogger(FinalizeSignupOnPayment.class);
    private static final String ACCOUNT_CREATED_TITLE = "Account Created!";
    private static final String ACCOUNT_CREATED_BODY = "Your onboarding payment reached 3 confirmations. Your account is now active.";

    private final SignupStateStore stateStore;
    private final UserServiceContract userService;
    private final PasskeyGateway passkeyGateway;
    private final OnboardingVoucherPort onboardingVoucherPort;
    private final UserNotifier userNotifier;
    private final CosignerSecretService cosignerSecretService;

    public FinalizeSignupOnPayment(SignupStateStore stateStore,
            UserServiceContract userService,
            PasskeyGateway passkeyGateway,
            OnboardingVoucherPort onboardingVoucherPort,
            UserNotifier userNotifier,
            CosignerSecretService cosignerSecretService) {
        this.stateStore = stateStore;
        this.userService = userService;
        this.passkeyGateway = passkeyGateway;
        this.onboardingVoucherPort = onboardingVoucherPort;
        this.userNotifier = userNotifier;
        this.cosignerSecretService = cosignerSecretService;
    }

    @Transactional
    public boolean execute(String sessionId, String txid, BigDecimal amountPaid) {
        if (amountPaid == null || amountPaid.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("finalizeSignupOnPayment: invalid amountPaid {} for session {}", amountPaid, sessionId);
            throw new IllegalArgumentException("amountPaid deve ser maior que zero.");
        }

        SignupState state = stateStore.findSignupState(sessionId);
        if (state == null) {
            log.warn(
                    "finalizeSignupOnPayment: SignupState for session {} not found - finalized, expired or not ready.",
                    sessionId);
            return false;
        }

        if (!state.isTotpVerified()) {
            log.warn("SignupState for session {} lacks TOTP verification.", sessionId);
            return false;
        }

        if (!state.isPasskeyRegistered()) {
            log.warn("SignupState for session {} lacks mandatory Passkey registration. Aborting finalization.", sessionId);
            return false;
        }

        try {
            UserDataBase user = resolveUser(state, sessionId);
            ensurePasskeyPresent(state, user);
            ensureOnboardingVoucherClaimed(user, txid, amountPaid);
            schedulePostCommitCleanup(sessionId, user.getId());

            log.info("Successfully finalized user {} via onboarding session {}", user.getUsername(), sessionId);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.warn(
                    "finalizeSignupOnPayment: concurrent finalization detected for session {}. State preserved for retry.",
                    sessionId,
                    e);
            throw e;
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
            throw new IllegalStateException("User was persisted but ID is null - aborting finalize.");
        }
        return user;
    }

    private UserDataBase createUserFromState(SignupState state) {
        UserDataBase user = new UserDataBase();
        user.setUsername(state.getUsername());
        user.setPassphrase(new String(state.getPassphrase()));
        user.setTOTPSecret(state.getTotpSecret());
        user.setIsActive(false);
        user.setAccountSecurity(state.getAccountSecurity() != null
                ? state.getAccountSecurity()
                : AccountSecurityType.STANDARD);
        user.setShamirTotalShares(state.getShamirTotalShares());
        user.setShamirThreshold(state.getShamirThreshold());
        user.setMultisigThreshold(state.getMultisigThreshold() != null ? state.getMultisigThreshold() : 2);
        user.setBackupCodes(state.getBackupCodes());
        return user;
    }

    private boolean needsCosignerSecret(UserDataBase user) {
        return user.getAccountSecurity() == AccountSecurityType.SHAMIR
                || user.getAccountSecurity() == AccountSecurityType.MULTISIG_2FA;
    }

    private void maybeAttachCosignerSecret(String sessionId, UserDataBase user) {
        if (needsCosignerSecret(user)) {
            String encryptedSecret = cosignerSecretService.generateAndEncrypt();
            user.setPlatformCosignerSecret(encryptedSecret);
            log.info("[Security] Platform co-signer secret generated for session {} mode {}",
                    sessionId, user.getAccountSecurity());
        }
    }

    private void ensurePasskeyPresent(SignupState state, UserDataBase user) {
        if (!state.isPasskeyRegistered()) {
            return;
        }
        List<PasskeyCredential> existingCredentials = passkeyGateway.findByUserId(user.getId());
        if (!existingCredentials.isEmpty()) {
            return;
        }

        PasskeyCredential credential = new PasskeyCredential();
        credential.setUser(user);
        credential.setDeviceName(state.getPasskeyDeviceName());
        credential.setPublicKeyCose(decodeBase64(publicKeyMaterial(state)));
        credential.setCredentialId(decodeBase64(state.getPasskeyCredentialId()));
        credential.setUserHandle(decodeBase64(state.getPasskeyUserHandle()));

        passkeyGateway.save(credential);
        log.info("Passkey (Ed25519) credential persisted for user {}", user.getUsername());
    }

    private void ensureOnboardingVoucherClaimed(UserDataBase user, String txid, BigDecimal amountPaid) {
        if (Boolean.TRUE.equals(user.getIsActive()) && user.getVoucher() != null) {
            return;
        }

        onboardingVoucherPort.createAndClaim(user.getId(), txid, amountPaid);
        UserDataBase refreshedUser = userService.buscarPorId(user.getId())
                .orElseThrow(() -> new IllegalStateException("User disappeared after onboarding voucher claim."));
        if (!Boolean.TRUE.equals(refreshedUser.getIsActive())) {
            throw new IllegalStateException("User was not activated after onboarding voucher claim.");
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
            log.warn("finalizeSignupOnPayment: failed to delete signup state for session {} after commit.",
                    sessionId, exception);
        }

        try {
            userNotifier.notify(userId, ACCOUNT_CREATED_TITLE, ACCOUNT_CREATED_BODY);
        } catch (RuntimeException exception) {
            log.warn("finalizeSignupOnPayment: user {} finalized but notification failed.", userId, exception);
        }
    }

    private static String publicKeyMaterial(SignupState state) {
        if (state.getPasskeyPublicKeyCose() != null) {
            return state.getPasskeyPublicKeyCose();
        }
        return state.getPasskeyPublicKey();
    }

    private static byte[] decodeBase64(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            return Base64.getUrlDecoder().decode(value);
        }
    }
}
