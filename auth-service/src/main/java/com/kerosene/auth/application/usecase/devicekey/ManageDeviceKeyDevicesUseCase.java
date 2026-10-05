package com.kerosene.auth.application.usecase.devicekey;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.kerosene.auth.application.infra.persistence.jpa.DeviceKeyCredentialRepository;
import com.kerosene.auth.application.infra.persistence.jpa.UserRepository;
import com.kerosene.auth.dto.devicekey.DeviceKeyDeviceDTO;
import com.kerosene.auth.model.entity.DeviceKeyCredential;
import com.kerosene.auth.model.entity.UserDataBase;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Lists and revokes device-key credentials belonging to an account. */
@Component
public class ManageDeviceKeyDevicesUseCase {

    /** Account lookup used to scope every credential operation to its owner. */
    private final UserRepository userRepository;
    /** Repository for the owner's registered device-key credentials. */
    private final DeviceKeyCredentialRepository deviceKeyRepository;

    /** Creates the device management use case. */
    /** @param userRepository account lookup repository */
    /** @param deviceKeyRepository credential query and persistence repository */
    public ManageDeviceKeyDevicesUseCase(
            UserRepository userRepository,
            DeviceKeyCredentialRepository deviceKeyRepository) {
        this.userRepository = userRepository;
        this.deviceKeyRepository = deviceKeyRepository;
    }

    /** Returns device summaries for an existing owner, or a not-found result. */
    /** @param userId authenticated account identifier */
    /** @return list result or user-not-found result */
    @Transactional(readOnly = true)
    public Result listDevices(Long userId) {
        UserDataBase user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return Result.userNotFound();
        }

        return Result.listed(devicesFor(user.getId()));
    }

    /** Marks an owner-scoped credential revoked and returns the refreshed device list. */
    /** @param userId authenticated account identifier */
    /** @param credentialId credential identifier requested for revocation */
    /** @return revoked result, or owner/credential not found */
    @Transactional
    public Result revokeDevice(Long userId, String credentialId) {
        UserDataBase user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return Result.userNotFound();
        }

        Optional<DeviceKeyCredential> credential =
                deviceKeyRepository.findByCredentialIdAndUserId(credentialId, user.getId());
        if (credential.isEmpty()) {
            return Result.credentialNotFound();
        }

        DeviceKeyCredential deviceKey = credential.get();
        deviceKey.setStatus("REVOKED");
        deviceKey.setRevokedAt(LocalDateTime.now());
        deviceKeyRepository.save(deviceKey);
        return Result.revoked(devicesFor(user.getId()));
    }

    /** Loads all credential rows for the owner and projects them to API device summaries. */
    /** @param userId owner identifier */
    /** @return immutable list of device summaries */
    private List<DeviceKeyDeviceDTO> devicesFor(Long userId) {
        return deviceKeyRepository.findByUserId(userId).stream()
                .map(DeviceKeyDeviceDTO::from)
                .toList();
    }

    /**
     * Result of listing or revoking a device-key credential.
     * @param status outcome of owner and credential lookup and requested operation
     * @param devices device summaries for successful list/revoke outcomes; otherwise {@code null}
     */
    public record Result(Status status, List<DeviceKeyDeviceDTO> devices) {

        /** Creates the owner-not-found outcome. */
        /** @return not-found result */
        private static Result userNotFound() {
            return new Result(Status.USER_NOT_FOUND, null);
        }

        /** Creates the credential-not-found outcome. */
        /** @return credential-not-found result */
        private static Result credentialNotFound() {
            return new Result(Status.CREDENTIAL_NOT_FOUND, null);
        }

        /** Creates the successful listing outcome. */
        /** @param devices owner's current device summaries */
        /** @return listed result */
        private static Result listed(List<DeviceKeyDeviceDTO> devices) {
            return new Result(Status.LISTED, devices);
        }

        /** Creates the successful revocation outcome. */
        /** @param devices refreshed device summaries after revocation */
        /** @return revoked result */
        private static Result revoked(List<DeviceKeyDeviceDTO> devices) {
            return new Result(Status.REVOKED, devices);
        }
    }

    /** Outcomes supported by device listing and revocation. */
    public enum Status {
        /** The owner's device list was returned. */
        LISTED,
        /** The credential was marked revoked and the updated list returned. */
        REVOKED,
        /** No account matched the authenticated owner identifier. */
        USER_NOT_FOUND,
        /** No credential with the supplied ID belongs to the owner. */
        CREDENTIAL_NOT_FOUND
    }
}
