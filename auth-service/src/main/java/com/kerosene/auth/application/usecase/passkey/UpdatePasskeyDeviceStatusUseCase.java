package com.kerosene.auth.application.usecase.passkey;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.kerosene.auth.application.infra.persistence.jpa.PasskeyCredentialRepository;
import com.kerosene.auth.application.infra.persistence.jpa.UserRepository;
import com.kerosene.auth.application.service.passkey.PasskeyInventoryService;
import com.kerosene.auth.dto.PasskeyInventoryDTO;
import com.kerosene.auth.model.entity.PasskeyCredential;
import com.kerosene.auth.model.entity.UserDataBase;

import java.util.Optional;

/** Changes one owner's passkey device status and returns the refreshed inventory. */
@Component
public class UpdatePasskeyDeviceStatusUseCase {

    /** Resolves the account so updates remain scoped to its authenticated owner. */
    private final UserRepository userRepository;
    /** Finds and persists the passkey credential selected by owner and installation ID. */
    private final PasskeyCredentialRepository passkeyCredentialRepository;
    /** Rebuilds the public inventory after the status mutation. */
    private final PasskeyInventoryService passkeyInventoryService;

    /** Creates the passkey device status operation. */
    /** @param userRepository account lookup repository */
    /** @param passkeyCredentialRepository credential repository */
    /** @param passkeyInventoryService inventory projection service */
    public UpdatePasskeyDeviceStatusUseCase(
            UserRepository userRepository,
            PasskeyCredentialRepository passkeyCredentialRepository,
            PasskeyInventoryService passkeyInventoryService) {
        this.userRepository = userRepository;
        this.passkeyCredentialRepository = passkeyCredentialRepository;
        this.passkeyInventoryService = passkeyInventoryService;
    }

    /** Loads the owner and installation-scoped credential, saves the requested status, and returns inventory. */
    /** @param userId account identifier */
    /** @param deviceInstallId installation identifier selecting the owner's device row */
    /** @param status new status value supplied to the use case */
    /** @return updated inventory, user-not-found, or device-not-found outcome */
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

    /**
     * Result of changing passkey device status.
     * @param status operation outcome
     * @param message optional public error text when the owner or device is absent
     * @param inventory refreshed projection for a successful update
     */
    public record Result(Status status, String message, PasskeyInventoryDTO inventory) {

        /** Builds a missing-user result. */
        /** @return user-not-found outcome */
        private static Result userNotFound() {
            return new Result(Status.USER_NOT_FOUND, "User not found", null);
        }

        /** Builds a missing-device result. */
        /** @return device-not-found outcome */
        private static Result deviceNotFound() {
            return new Result(Status.DEVICE_NOT_FOUND, "Device not found", null);
        }

        /** Builds a successful result with the updated inventory. */
        /** @param inventory current inventory after the status change */
        /** @return updated outcome */
        private static Result updated(PasskeyInventoryDTO inventory) {
            return new Result(Status.UPDATED, null, inventory);
        }
    }

    /** Outcomes of updating passkey device status. */
    public enum Status {
        /** Device status was saved and the refreshed inventory returned. */
        UPDATED,
        /** No account matched the requested owner ID. */
        USER_NOT_FOUND,
        /** No passkey device matched the owner and installation ID. */
        DEVICE_NOT_FOUND
    }
}
