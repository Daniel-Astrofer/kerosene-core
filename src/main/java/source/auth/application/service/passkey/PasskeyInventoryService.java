package source.auth.application.service.passkey;

import org.springframework.stereotype.Service;
import source.auth.application.infra.persistence.jpa.PasskeyCredentialRepository;
import source.auth.dto.PasskeyActionRequiredDTO;
import source.auth.dto.PasskeyDeviceDTO;
import source.auth.dto.PasskeyInventoryDTO;
import source.auth.model.entity.PasskeyCredential;
import source.auth.model.entity.UserDataBase;

import java.util.Base64;
import java.util.List;

@Service
public class PasskeyInventoryService {

    private final PasskeyCredentialRepository passkeyCredentialRepository;
    private final PasskeyService passkeyService;

    public PasskeyInventoryService(
            PasskeyCredentialRepository passkeyCredentialRepository,
            PasskeyService passkeyService) {
        this.passkeyCredentialRepository = passkeyCredentialRepository;
        this.passkeyService = passkeyService;
    }

    public PasskeyInventoryDTO inventoryFor(UserDataBase user) {
        List<PasskeyCredential> credentials = passkeyCredentialRepository.findByUserId(user.getId());
        String currentRpId = passkeyService.resolveCurrentRelyingPartyId();
        String currentHost = passkeyService.resolveCurrentRequestHost();

        List<PasskeyDeviceDTO> devices = credentials.stream()
                .map(credential -> toDevice(credential, currentRpId, currentHost))
                .toList();

        boolean compatibleForCurrentLogin = devices.stream().anyMatch(PasskeyDeviceDTO::compatibleWithCurrentLogin);
        boolean legacyCredentialsPresent = devices.stream()
                .anyMatch(device -> "UNKNOWN".equals(device.compatibilityStatus()));

        return new PasskeyInventoryDTO(
                !devices.isEmpty(),
                compatibleForCurrentLogin,
                legacyCredentialsPresent,
                currentRpId,
                currentHost,
                devices);
    }

    public boolean hasUsablePasskeyForCurrentLogin(UserDataBase user) {
        PasskeyInventoryDTO inventory = inventoryFor(user);
        return inventory.compatibleForCurrentLogin() || inventory.legacyCredentialsPresent();
    }

    public boolean isKnownIncompatibleForCurrentLogin(PasskeyCredential credential) {
        return compatibilityOf(
                credential,
                passkeyService.resolveCurrentRelyingPartyId(),
                passkeyService.resolveCurrentRequestHost()) == CompatibilityStatus.INCOMPATIBLE;
    }

    public PasskeyActionRequiredDTO buildChallengeRequired(UserDataBase user, String challenge, String reason) {
        PasskeyInventoryDTO inventory = inventoryFor(user);
        return new PasskeyActionRequiredDTO(
                "ASSERT_PASSKEY",
                reason,
                challenge,
                user.hasTotpEnabled(),
                shouldLinkNewPasskey(user, inventory),
                "/settings/security/passkeys",
                guidanceFor(user, inventory, true),
                inventory);
    }

    public PasskeyActionRequiredDTO buildLinkNewPasskeyGuidance(UserDataBase user, String reason) {
        PasskeyInventoryDTO inventory = inventoryFor(user);
        return new PasskeyActionRequiredDTO(
                "LINK_NEW_PASSKEY",
                reason,
                null,
                user.hasTotpEnabled(),
                shouldLinkNewPasskey(user, inventory),
                "/settings/security/passkeys",
                guidanceFor(user, inventory, false),
                inventory);
    }

    private PasskeyDeviceDTO toDevice(PasskeyCredential credential, String currentRpId, String currentHost) {
        CompatibilityStatus compatibility = compatibilityOf(credential, currentRpId, currentHost);
        String deviceName = hasText(credential.getDeviceName()) ? credential.getDeviceName() : "Passkey sem nome";
        String credentialId = credential.getCredentialId() == null
                ? null
                : Base64.getUrlEncoder().withoutPadding().encodeToString(credential.getCredentialId());

        return new PasskeyDeviceDTO(
                credentialId,
                deviceName,
                credential.getRelyingPartyId(),
                credential.getOriginHost(),
                compatibility.name(),
                compatibility == CompatibilityStatus.COMPATIBLE);
    }

    private CompatibilityStatus compatibilityOf(
            PasskeyCredential credential,
            String currentRpId,
            String currentHost) {
        if (!hasText(currentRpId) && !hasText(currentHost)) {
            return CompatibilityStatus.UNKNOWN;
        }

        boolean hasRpIdMetadata = hasText(credential.getRelyingPartyId());
        boolean hasOriginMetadata = hasText(credential.getOriginHost());
        if (!hasRpIdMetadata && !hasOriginMetadata) {
            return CompatibilityStatus.UNKNOWN;
        }

        if (matches(credential.getRelyingPartyId(), currentRpId)
                || matches(credential.getRelyingPartyId(), currentHost)
                || matches(credential.getOriginHost(), currentHost)) {
            return CompatibilityStatus.COMPATIBLE;
        }

        return CompatibilityStatus.INCOMPATIBLE;
    }

    private boolean shouldLinkNewPasskey(UserDataBase user, PasskeyInventoryDTO inventory) {
        return user.hasTotpEnabled()
                && !inventory.compatibleForCurrentLogin()
                && !inventory.legacyCredentialsPresent();
    }

    private String guidanceFor(UserDataBase user, PasskeyInventoryDTO inventory, boolean canRetryWithChallenge) {
        if (shouldLinkNewPasskey(user, inventory)) {
            return "A passkey atual nao atende o login deste dispositivo. Entre com senha + TOTP e vincule uma nova passkey.";
        }
        if (canRetryWithChallenge) {
            return "Assine o challenge com uma passkey vinculada a este login para concluir a operacao.";
        }
        return "Use uma passkey registrada neste login ou vincule outra passkey compatível.";
    }

    private boolean matches(String left, String right) {
        return hasText(left) && hasText(right) && left.equalsIgnoreCase(right);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private enum CompatibilityStatus {
        COMPATIBLE,
        INCOMPATIBLE,
        UNKNOWN
    }
}
