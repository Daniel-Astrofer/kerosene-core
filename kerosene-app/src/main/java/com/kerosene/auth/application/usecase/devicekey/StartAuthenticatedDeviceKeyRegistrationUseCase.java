package source.auth.application.usecase.devicekey;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import source.auth.application.infra.persistence.jpa.UserRepository;
import source.auth.application.service.devicekey.DeviceKeyService;
import source.auth.dto.devicekey.DeviceKeyChallengeResponse;
import source.auth.model.entity.UserDataBase;

@Component
public class StartAuthenticatedDeviceKeyRegistrationUseCase {

    private final UserRepository userRepository;
    private final DeviceKeyService deviceKeyService;

    public StartAuthenticatedDeviceKeyRegistrationUseCase(
            UserRepository userRepository,
            DeviceKeyService deviceKeyService) {
        this.userRepository = userRepository;
        this.deviceKeyService = deviceKeyService;
    }

    @Transactional(readOnly = true)
    public Result execute(Long userId) {
        UserDataBase user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return Result.userNotFound();
        }

        return Result.generated(deviceKeyService.startAuthenticatedRegistrationChallenge(user));
    }

    public record Result(Status status, DeviceKeyChallengeResponse challenge) {

        public static Result userNotFound() {
            return new Result(Status.USER_NOT_FOUND, null);
        }

        public static Result generated(DeviceKeyChallengeResponse challenge) {
            return new Result(Status.GENERATED, challenge);
        }
    }

    public enum Status {
        GENERATED,
        USER_NOT_FOUND
    }
}
