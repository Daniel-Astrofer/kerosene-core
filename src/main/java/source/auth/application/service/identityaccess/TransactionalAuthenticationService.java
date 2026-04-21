package source.auth.application.service.identityaccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import source.auth.AuthExceptions;
import source.auth.application.infra.persistence.jpa.PasskeyCredentialRepository;
import source.auth.application.service.cripto.contracts.Hasher;
import source.auth.application.service.passkey.PasskeyService;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.application.service.validation.totp.contracts.TOTPVerifier;
import source.auth.model.entity.PasskeyCredential;
import source.auth.model.entity.UserDataBase;
import source.auth.model.enums.AccountSecurityType;

import java.util.Base64;

@Service
public class TransactionalAuthenticationService implements TransactionalAuthenticationPort {

    private static final Logger log = LoggerFactory.getLogger(TransactionalAuthenticationService.class);

    private final PasskeyService passkeyService;
    private final PasskeyCredentialRepository passkeyCredentialRepository;
    private final TOTPVerifier totpVerifier;
    private final Hasher hasher;
    private final UserServiceContract userService;
    private final PlatformTransactionSignerPort platformTransactionSigner;
    private final ObjectMapper objectMapper;

    public TransactionalAuthenticationService(
            PasskeyService passkeyService,
            PasskeyCredentialRepository passkeyCredentialRepository,
            TOTPVerifier totpVerifier,
            @Qualifier("Argon2Hasher") Hasher hasher,
            UserServiceContract userService,
            PlatformTransactionSignerPort platformTransactionSigner,
            ObjectMapper objectMapper) {
        this.passkeyService = passkeyService;
        this.passkeyCredentialRepository = passkeyCredentialRepository;
        this.totpVerifier = totpVerifier;
        this.hasher = hasher;
        this.userService = userService;
        this.platformTransactionSigner = platformTransactionSigner;
        this.objectMapper = objectMapper;
    }

    @Override
    public TransactionalAuthenticationResult authorize(TransactionalAuthenticationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Transactional authentication request is required.");
        }

        UserDataBase user = resolveUser(request);
        validateResourceOwnership(request, user);
        AccountSecurityType accountSecurity = resolveAccountSecurity(user);

        log.info(
                "Verifying transactional auth for user: {} (security: {}, scope: {})",
                user.getUsername(),
                accountSecurity,
                request.scope());

        boolean totpValid = false;
        if (request.scope() == TransactionalAuthenticationScope.WALLET_OUTBOUND) {
            totpValid = verifyTotpIfRequiredOrPresented(user, request, accountSecurity);
        }
        boolean passphraseValid = verifyPassphraseIfPresented(user, request.confirmationPassphrase());
        if (request.scope() != TransactionalAuthenticationScope.WALLET_OUTBOUND) {
            totpValid = verifyTotpIfRequiredOrPresented(user, request, accountSecurity);
        }
        boolean passkeyValid = verifyPasskeyIfPresented(user, request.passkeyAssertionJson());

        enforceSecurityPolicy(user, accountSecurity, passphraseValid, totpValid, passkeyValid);

        String platformSignature = "";
        if (request.scope().platformSignatureRequired() && requiresPlatformSignature(accountSecurity)) {
            if (!platformTransactionSigner.isAvailable()) {
                throw new AuthExceptions.AuthValidationException(
                        "Advanced account security mode is configured, but platform co-signing is not available.");
            }
            platformSignature = platformTransactionSigner.sign(user);
        }

        return new TransactionalAuthenticationResult(user, platformSignature);
    }

    private UserDataBase resolveUser(TransactionalAuthenticationRequest request) {
        if (request.user() != null) {
            return request.user();
        }
        if (request.authenticatedUserId() == null) {
            throw new AuthExceptions.InvalidCredentials("Authenticated user is required for this operation.");
        }
        return userService.buscarPorId(request.authenticatedUserId())
                .orElseThrow(() -> new AuthExceptions.UserNotFoundException("Usuário não encontrado."));
    }

    private void validateResourceOwnership(TransactionalAuthenticationRequest request, UserDataBase user) {
        if (request.scope() == TransactionalAuthenticationScope.WALLET_OUTBOUND
                && request.resourceOwnerUserId() == null) {
            throw new IllegalArgumentException("Wallet does not belong to the authenticated user.");
        }
        if (request.resourceOwnerUserId() == null) {
            return;
        }
        if (user.getId() == null || !request.resourceOwnerUserId().equals(user.getId())) {
            throw new IllegalArgumentException("Wallet does not belong to the authenticated user.");
        }
        if (request.authenticatedUserId() != null && !request.authenticatedUserId().equals(user.getId())) {
            throw new IllegalArgumentException("Wallet does not belong to the authenticated user.");
        }
    }

    private AccountSecurityType resolveAccountSecurity(UserDataBase user) {
        return user.getAccountSecurity() != null ? user.getAccountSecurity() : AccountSecurityType.STANDARD;
    }

    private boolean verifyPassphraseIfPresented(UserDataBase user, String confirmationPassphrase) {
        if (!hasText(confirmationPassphrase)) {
            return false;
        }
        if (!hasher.verify(confirmationPassphrase.toCharArray(), user.getPassphrase())) {
            throw new AuthExceptions.InvalidPassphrase("Invalid passphrase for transaction authorization.");
        }
        log.info("Transaction passphrase factor verified for {}", user.getUsername());
        return true;
    }

    private boolean verifyTotpIfRequiredOrPresented(
            UserDataBase user,
            TransactionalAuthenticationRequest request,
            AccountSecurityType accountSecurity) {
        boolean required = requiresTotp(accountSecurity);
        boolean presented = hasText(request.totpCode());
        if (!required && !(request.scope() == TransactionalAuthenticationScope.LEDGER_TRANSFER && presented)) {
            return false;
        }
        if (!presented) {
            throw missingTotpException(request.scope(), accountSecurity);
        }
        if (!hasText(request.totpSecret())) {
            throw new AuthExceptions.IncorrectTotpException("TOTP not configured for this operation.");
        }

        if (request.scope() == TransactionalAuthenticationScope.WALLET_OUTBOUND) {
            if (!totpVerifier.totpMatcher(request.totpSecret(), request.totpCode())) {
                throw new AuthExceptions.IncorrectTotpException("Invalid wallet TOTP code.");
            }
        } else {
            totpVerifier.totpVerify(request.totpSecret(), request.totpCode());
        }
        log.info("Transaction TOTP factor verified for {}", user.getUsername());
        return true;
    }

    private AuthExceptions.IncorrectTotpException missingTotpException(
            TransactionalAuthenticationScope scope,
            AccountSecurityType accountSecurity) {
        if (scope == TransactionalAuthenticationScope.WALLET_OUTBOUND) {
            return new AuthExceptions.IncorrectTotpException("TOTP code is required for wallet authorization.");
        }
        if (accountSecurity == AccountSecurityType.SHAMIR) {
            return new AuthExceptions.IncorrectTotpException(
                    "A valid TOTP code is required for Shamir-protected transactions.");
        }
        return new AuthExceptions.IncorrectTotpException(
                "A valid TOTP code is required for multisig vault transactions.");
    }

    private boolean verifyPasskeyIfPresented(UserDataBase user, String assertionJson) {
        if (!hasText(assertionJson)) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(assertionJson);
            String signature = requiredText(node, "signature");
            String authData = requiredText(node, "authData");
            String clientDataJSON = requiredText(node, "clientDataJSON");
            String credentialId = requiredText(node, "credentialId");

            byte[] credentialIdBytes = decodeCredentialId(credentialId);
            PasskeyCredential credential = passkeyCredentialRepository
                    .findByCredentialIdAndUserId(credentialIdBytes, user.getId())
                    .orElseThrow(() -> new AuthExceptions.AuthValidationException(
                            "Passkey credential not found for this user."));

            String consumedChallenge = passkeyService.consumeChallengeFromRedis(user.getUsername());
            if (consumedChallenge == null) {
                throw new AuthExceptions.AuthValidationException("Passkey challenge expired. Please retry.");
            }

            boolean passkeyValid = passkeyService.verifySignature(
                    user.getUsername(),
                    consumedChallenge,
                    signature,
                    credential.getPublicKeyCose(),
                    authData,
                    clientDataJSON);
            if (!passkeyValid) {
                throw new AuthExceptions.AuthValidationException("Invalid passkey signature.");
            }

            log.info("Transaction passkey factor verified for {}", user.getUsername());
            return true;
        } catch (AuthExceptions.AuthValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Passkey verification failed during transactional authorization", exception);
            throw new AuthExceptions.AuthValidationException("Passkey validation failed: " + exception.getMessage());
        }
    }

    private void enforceSecurityPolicy(
            UserDataBase user,
            AccountSecurityType accountSecurity,
            boolean passphraseValid,
            boolean totpValid,
            boolean passkeyValid) {
        switch (accountSecurity) {
            case PASSKEY, STANDARD -> requirePasskey(user, passkeyValid);
            case SHAMIR -> {
                if (!passphraseValid) {
                    throw new AuthExceptions.InvalidCredentials(
                            "This account requires confirmation reconstructed from your SLIP-39 shares.");
                }
                if (!totpValid) {
                    throw new AuthExceptions.IncorrectTotpException(
                            "A valid TOTP code is required for Shamir-protected transactions.");
                }
            }
            case MULTISIG_2FA -> {
                if (!passphraseValid) {
                    throw new AuthExceptions.InvalidCredentials(
                            "This multisig vault requires passphrase confirmation.");
                }
                if (!totpValid) {
                    throw new AuthExceptions.IncorrectTotpException(
                            "A valid TOTP code is required for multisig vault transactions.");
                }
                int threshold = user.getMultisigThreshold() != null ? user.getMultisigThreshold() : 2;
                if (threshold >= 3) {
                    requirePasskey(user, passkeyValid);
                }
            }
        }
    }

    private boolean requiresTotp(AccountSecurityType accountSecurity) {
        return accountSecurity == AccountSecurityType.SHAMIR
                || accountSecurity == AccountSecurityType.MULTISIG_2FA;
    }

    private boolean requiresPlatformSignature(AccountSecurityType accountSecurity) {
        return accountSecurity == AccountSecurityType.SHAMIR
                || accountSecurity == AccountSecurityType.MULTISIG_2FA;
    }

    private void requirePasskey(UserDataBase user, boolean passkeyValid) {
        if (passkeyValid) {
            return;
        }
        String challenge = passkeyService.generateChallenge(user.getUsername());
        throw new AuthExceptions.AuthValidationException("PASSKEY_CHALLENGE_REQUIRED:" + challenge);
    }

    private String requiredText(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText(null);
        if (!hasText(value)) {
            throw new AuthExceptions.AuthValidationException("Passkey assertion missing field: " + fieldName);
        }
        return value;
    }

    private byte[] decodeCredentialId(String credentialId) {
        try {
            return Base64.getUrlDecoder().decode(credentialId);
        } catch (IllegalArgumentException urlException) {
            try {
                return Base64.getDecoder().decode(credentialId);
            } catch (IllegalArgumentException standardException) {
                throw new AuthExceptions.AuthValidationException("Invalid passkey credential id encoding.");
            }
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
