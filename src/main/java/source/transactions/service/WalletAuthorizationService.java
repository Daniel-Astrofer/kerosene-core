package source.transactions.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import source.auth.AuthExceptions;
import source.auth.application.infra.persistance.jpa.PasskeyCredentialRepository;
import source.auth.application.service.cripto.contracts.Hasher;
import source.auth.application.service.passkey.PasskeyService;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.application.service.validation.totp.contratcs.TOTPVerifier;
import source.auth.model.entity.UserDataBase;
import source.auth.model.enums.AccountSecurityType;
import source.transactions.infra.MpcSidecarClient;
import source.wallet.model.WalletEntity;

import java.util.Base64;

@Service
public class WalletAuthorizationService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WalletAuthorizationService.class);

    private final TOTPVerifier totpVerifier;
    private final PasskeyService passkeyService;
    private final PasskeyCredentialRepository passkeyCredentialRepository;
    private final UserServiceContract userService;
    private final Hasher hasher;
    private final MpcSidecarClient mpcClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WalletAuthorizationService(
            TOTPVerifier totpVerifier,
            PasskeyService passkeyService,
            PasskeyCredentialRepository passkeyCredentialRepository,
            UserServiceContract userService,
            @Qualifier("Argon2Hasher") Hasher hasher,
            MpcSidecarClient mpcClient) {
        this.totpVerifier = totpVerifier;
        this.passkeyService = passkeyService;
        this.passkeyCredentialRepository = passkeyCredentialRepository;
        this.userService = userService;
        this.hasher = hasher;
        this.mpcClient = mpcClient;
    }

    public AuthorizationResult authorizeOutboundTransfer(
            Long userId,
            WalletEntity wallet,
            String totpCode,
            String passkeyAssertionResponseJSON,
            String confirmationPassphrase) {
        if (wallet == null || wallet.getUser() == null || !wallet.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Wallet does not belong to the authenticated user.");
        }

        UserDataBase user = userService.buscarPorId(userId)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado."));

        if (totpCode == null || totpCode.isBlank()) {
            throw new AuthExceptions.IncorrectTotpException("TOTP code is required for wallet authorization.");
        }
        if (!totpVerifier.totpMatcher(wallet.getTotpSecret(), totpCode)) {
            throw new AuthExceptions.IncorrectTotpException("Invalid wallet TOTP code.");
        }

        boolean passphraseValid = false;
        boolean passkeyValid = false;

        if (confirmationPassphrase != null && !confirmationPassphrase.isBlank()) {
            if (!hasher.verify(confirmationPassphrase.toCharArray(), user.getPassphrase())) {
                throw new AuthExceptions.InvalidPassphrase("Invalid passphrase for this operation.");
            }
            passphraseValid = true;
        }

        if (passkeyAssertionResponseJSON != null && !passkeyAssertionResponseJSON.isBlank()) {
            passkeyValid = verifyPasskeyAssertion(user, passkeyAssertionResponseJSON);
        }

        String platformSignature = "";
        if (user.getAccountSecurity() == AccountSecurityType.SHAMIR) {
            if (!passphraseValid) {
                throw new AuthExceptions.InvalidCredentials(
                        "Shamir protected accounts require the reconstructed passphrase for outbound authorizations.");
            }
            platformSignature = signWithPlatformMpc(user);
        } else if (user.getAccountSecurity() == AccountSecurityType.MULTISIG_2FA) {
            if (!passphraseValid) {
                throw new AuthExceptions.InvalidCredentials(
                        "The account passphrase is mandatory for multisig outbound authorizations.");
            }
            int threshold = user.getMultisigThreshold() != null ? user.getMultisigThreshold() : 2;
            if (threshold >= 3 && !passkeyValid) {
                throwPasskeyChallengeRequired(user);
            }
            platformSignature = signWithPlatformMpc(user);
        } else if (user.getAccountSecurity() == AccountSecurityType.PASSKEY
                || Boolean.TRUE.equals(user.getPasskeyEnabledForTransactions())) {
            if (!passkeyValid) {
                throwPasskeyChallengeRequired(user);
            }
        }

        return new AuthorizationResult(user, platformSignature);
    }

    public record AuthorizationResult(
            UserDataBase user,
            String platformSignature) {
    }

    private boolean verifyPasskeyAssertion(UserDataBase user, String passkeyAssertionResponseJSON) {
        try {
            JsonNode node = objectMapper.readTree(passkeyAssertionResponseJSON);
            String signature = node.get("signature").asText();
            String authData = node.get("authData").asText();
            String clientDataJSON = node.get("clientDataJSON").asText();
            String credentialId = node.get("credentialId").asText();

            byte[] credentialIdBytes;
            try {
                credentialIdBytes = Base64.getUrlDecoder().decode(credentialId);
            } catch (Exception e) {
                credentialIdBytes = Base64.getDecoder().decode(credentialId);
            }

            java.util.Optional<source.auth.model.entity.PasskeyCredential> credential = passkeyCredentialRepository
                    .findByCredentialIdAndUserId(credentialIdBytes, user.getId());
            if (credential.isEmpty()) {
                throw new AuthExceptions.AuthValidationException("Passkey credential not found for this user.");
            }

            String consumedChallenge = passkeyService.consumeChallengeFromRedis(user.getUsername());
            if (consumedChallenge == null) {
                throw new AuthExceptions.AuthValidationException("Passkey challenge expired. Please retry.");
            }

            boolean valid = passkeyService.verifySignature(
                    user.getUsername(),
                    consumedChallenge,
                    signature,
                    credential.get().getPublicKeyCose(),
                    authData,
                    clientDataJSON);
            if (!valid) {
                throw new AuthExceptions.AuthValidationException("Invalid passkey signature.");
            }
            return true;
        } catch (AuthExceptions.AuthValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Passkey verification failed during outbound authorization", ex);
            throw new AuthExceptions.AuthValidationException("Passkey validation failed: " + ex.getMessage());
        }
    }

    private String signWithPlatformMpc(UserDataBase user) {
        byte[] messageHash = new byte[32];
        byte[] signature = mpcClient.sign(user.getUsername(), messageHash, "TARGET_PUBKEY");
        return "_MPC_SIGNED_" + Base64.getEncoder().encodeToString(signature).substring(0, 10);
    }

    private void throwPasskeyChallengeRequired(UserDataBase user) {
        String challenge = passkeyService.generateChallenge(user.getUsername());
        throw new AuthExceptions.AuthValidationException("PASSKEY_CHALLENGE_REQUIRED:" + challenge);
    }
}
