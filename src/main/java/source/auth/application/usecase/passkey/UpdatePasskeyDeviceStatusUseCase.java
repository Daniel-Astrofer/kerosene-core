package source.auth.application.usecase.passkey;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import source.auth.application.infra.persistence.jpa.PasskeyCredentialRepository;
import source.auth.application.infra.persistence.jpa.UserRepository;
import source.auth.application.service.passkey.PasskeyInventoryService;
import source.auth.dto.PasskeyInventoryDTO;
import source.auth.model.entity.PasskeyCredential;
import source.auth.model.entity.UserDataBase;

import java.util.Optional;

@Component
public class UpdatePasskeyDeviceStatusUseCase {

    private final UserRepository userRepository;
    private final PasskeyCredentialRepository passkeyCredentialRepository;
    private final PasskeyInventoryService passkeyInventoryService;

    public UpdatePasskeyDeviceStatusUseCase(
            UserRepository userRepository,
            PasskeyCredentialRepository passkeyCredentialRepository,
            PasskeyInventoryService passkeyInventoryService) {
        this.userRepository = userRepository;
        this.passkeyCredentialRepository = passkeyCredentialRepository;
        this.passkeyInventoryService = passkeyInventoryService;
    }

    @Transactional
    public Result execute(Long userId, String deviceInstallId, String status) {
        UserDataBase user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return Result.userNotFound();
        }

        Optional<PasskeyCredential> credential = passkeyCredentialRepository
                .findFirstByUserIdAndDeviceInstallId(user.getId(), deviceInstallId);
        if (credential.isEmpty()) {
            return Result.deviceNotFound();
        }

        PasskeyCredential device = credential.get();
        device.setStatus(status);
        passkeyCredentialRepository.save(device);
        return Result.updated(passkeyInventoryService.inventoryFor(user));
    }

    public record Result(Status status, String message, PasskeyInventoryDTO inventory) {

        private static Result userNotFound() {
            return new Result(Status.USER_NOT_FOUND, "User not found", null);
        }

        private static Result deviceNotFound() {
            return new Result(Status.DEVICE_NOT_FOUND, "Device not found", null);
        }

        private static Result updated(PasskeyInventoryDTO inventory) {
            return new Result(Status.UPDATED, null, inventory);
        }
    }

    public enum Status {
        UPDATED,
        USER_NOT_FOUND,
        DEVICE_NOT_FOUND
    }
}
