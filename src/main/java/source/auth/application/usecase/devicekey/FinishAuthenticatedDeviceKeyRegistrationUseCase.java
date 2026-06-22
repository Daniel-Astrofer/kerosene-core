package source.auth.application.usecase.devicekey;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import source.auth.application.infra.persistence.jpa.DeviceKeyCredentialRepository;
import source.auth.application.infra.persistence.jpa.UserRepository;
import source.auth.application.service.devicekey.DeviceKeyService;
import source.auth.dto.devicekey.DeviceKeyRegistrationRequest;
import source.auth.model.entity.DeviceKeyCredential;
import source.auth.model.entity.UserDataBase;

@Component
public class FinishAuthenticatedDeviceKeyRegistrationUseCase {

    private final UserRepository userRepository;
    private final DeviceKeyCredentialRepository deviceKeyRepository;
    private final DeviceKeyService deviceKeyService;

    public FinishAuthenticatedDeviceKeyRegistrationUseCase(
            UserRepository userRepository,
            DeviceKeyCredentialRepository deviceKeyRepository,
            DeviceKeyService deviceKeyService) {
        this.userRepository = userRepository;
        this.deviceKeyRepository = deviceKeyRepository;
        this.deviceKeyService = deviceKeyService;
    }

    @Transactional
    public Result execute(Long userId, DeviceKeyRegistrationRequest request) {
        UserDataBase user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return Result.userNotFound();
        }

        DeviceKeyService.VerifiedDeviceKeyRegistration verified =
                deviceKeyService.verifyRegistration(request, "", user.getUsername());
        persistDeviceKey(user, verified);
        return Result.registered();
    }

    private void persistDeviceKey(
            UserDataBase user,
            DeviceKeyService.VerifiedDeviceKeyRegistration verified) {
        if (deviceKeyRepository.findByCredentialIdAndUserId(verified.credentialId(), user.getId()).isPresent()) {
            return;
        }

        DeviceKeyCredential credential = new DeviceKeyCredential();
        credential.setUser(user);
        credential.setCredentialId(verified.credentialId());
        credential.setUserHandle(verified.userHandle());
        credential.setPublicKeyEd25519(verified.publicKeyEd25519());
        credential.setAlgorithm(DeviceKeyService.ALGORITHM);
        credential.setCounter(verified.counter());
        credential.setDeviceName(verified.deviceName());
        credential.setDeviceInstallId(verified.deviceInstallId());
        credential.setKeyStorage(verified.keyStorage());
        credential.setPlatform(verified.platform());
        credential.setBrowser(verified.browser());
        credential.setBrand(verified.brand());
        credential.setModel(verified.model());
        credential.setSerialNumber(verified.serialNumber());
        credential.setOnionServiceId(verified.onionServiceId());
        credential.setProtocolVersion(1);
        credential.setStatus("ACTIVE");
        deviceKeyRepository.save(credential);
    }

    public record Result(Status status) {

        public static Result registered() {
            return new Result(Status.REGISTERED);
        }

        public static Result userNotFound() {
            return new Result(Status.USER_NOT_FOUND);
        }
    }

    public enum Status {
        REGISTERED,
        USER_NOT_FOUND
    }
}
