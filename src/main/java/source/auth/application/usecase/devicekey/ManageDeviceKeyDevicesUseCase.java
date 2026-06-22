package source.auth.application.usecase.devicekey;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import source.auth.application.infra.persistence.jpa.DeviceKeyCredentialRepository;
import source.auth.application.infra.persistence.jpa.UserRepository;
import source.auth.dto.devicekey.DeviceKeyDeviceDTO;
import source.auth.model.entity.DeviceKeyCredential;
import source.auth.model.entity.UserDataBase;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class ManageDeviceKeyDevicesUseCase {

    private final UserRepository userRepository;
    private final DeviceKeyCredentialRepository deviceKeyRepository;

    public ManageDeviceKeyDevicesUseCase(
            UserRepository userRepository,
            DeviceKeyCredentialRepository deviceKeyRepository) {
        this.userRepository = userRepository;
        this.deviceKeyRepository = deviceKeyRepository;
    }

    @Transactional(readOnly = true)
    public Result listDevices(Long userId) {
        UserDataBase user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return Result.userNotFound();
        }

        return Result.listed(devicesFor(user.getId()));
    }

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

    private List<DeviceKeyDeviceDTO> devicesFor(Long userId) {
        return deviceKeyRepository.findByUserId(userId).stream()
                .map(DeviceKeyDeviceDTO::from)
                .toList();
    }

    public record Result(Status status, List<DeviceKeyDeviceDTO> devices) {

        private static Result userNotFound() {
            return new Result(Status.USER_NOT_FOUND, null);
        }

        private static Result credentialNotFound() {
            return new Result(Status.CREDENTIAL_NOT_FOUND, null);
        }

        private static Result listed(List<DeviceKeyDeviceDTO> devices) {
            return new Result(Status.LISTED, devices);
        }

        private static Result revoked(List<DeviceKeyDeviceDTO> devices) {
            return new Result(Status.REVOKED, devices);
        }
    }

    public enum Status {
        LISTED,
        REVOKED,
        USER_NOT_FOUND,
        CREDENTIAL_NOT_FOUND
    }
}
