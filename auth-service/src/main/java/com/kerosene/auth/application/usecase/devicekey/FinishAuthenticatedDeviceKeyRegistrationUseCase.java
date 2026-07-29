package com.kerosene.auth.application.usecase.devicekey;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.kerosene.auth.application.infra.persistence.jpa.DeviceKeyCredentialRepository;
import com.kerosene.auth.application.infra.persistence.jpa.UserRepository;
import com.kerosene.auth.application.service.devicebinding.DeviceBindingPolicy;
import com.kerosene.auth.application.service.devicekey.DeviceKeyService;
import com.kerosene.auth.dto.devicebinding.DeviceAlreadyBoundDTO;
import com.kerosene.auth.dto.devicekey.DeviceKeyRegistrationRequest;
import com.kerosene.auth.model.entity.DeviceKeyCredential;
import com.kerosene.auth.model.entity.UserDataBase;

@Component
public class FinishAuthenticatedDeviceKeyRegistrationUseCase {

    private final UserRepository userRepository;
    private final DeviceKeyCredentialRepository deviceKeyRepository;
    private final DeviceKeyService deviceKeyService;
    private final DeviceBindingPolicy deviceBindingPolicy;

    public FinishAuthenticatedDeviceKeyRegistrationUseCase(
            UserRepository userRepository,
            DeviceKeyCredentialRepository deviceKeyRepository,
            DeviceKeyService deviceKeyService,
            DeviceBindingPolicy deviceBindingPolicy) {
        this.userRepository = userRepository;
        this.deviceKeyRepository = deviceKeyRepository;
        this.deviceKeyService = deviceKeyService;
        this.deviceBindingPolicy = deviceBindingPolicy;
    }

    @Transactional
    public Result execute(Long userId, DeviceKeyRegistrationRequest request) {
        UserDataBase user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return Result.userNotFound();
        }

        DeviceKeyService.VerifiedDeviceKeyRegistration verified =
                deviceKeyService.verifyRegistration(request, "", user.getUsername());
        var bindingConflict = deviceBindingPolicy.ensureDeviceAvailableForBind(
                verified.deviceInstallId(),
                user.getId(),
                request.isConfirmUnlinkDevice());
        if (bindingConflict != null) {
            return Result.deviceAlreadyBound(bindingConflict);
        }
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

    public record Result(Status status, DeviceAlreadyBoundDTO deviceAlreadyBound) {

        public static Result registered() {
            return new Result(Status.REGISTERED, null);
        }

        public static Result userNotFound() {
            return new Result(Status.USER_NOT_FOUND, null);
        }

        public static Result deviceAlreadyBound(DeviceAlreadyBoundDTO payload) {
            return new Result(Status.DEVICE_ALREADY_BOUND, payload);
        }
    }

    public enum Status {
        REGISTERED,
        USER_NOT_FOUND,
        DEVICE_ALREADY_BOUND
    }
}
