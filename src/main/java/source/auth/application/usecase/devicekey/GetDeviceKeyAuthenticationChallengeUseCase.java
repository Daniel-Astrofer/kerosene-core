package source.auth.application.usecase.devicekey;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import source.auth.application.infra.persistence.jpa.UserRepository;
import source.auth.application.service.devicekey.DeviceKeyService;
import source.auth.dto.devicekey.DeviceKeyChallengeResponse;
import source.auth.model.entity.UserDataBase;

import java.util.Locale;

@Component
public class GetDeviceKeyAuthenticationChallengeUseCase {

    private final UserRepository userRepository;
    private final DeviceKeyService deviceKeyService;

    public GetDeviceKeyAuthenticationChallengeUseCase(
            UserRepository userRepository,
            DeviceKeyService deviceKeyService) {
        this.userRepository = userRepository;
        this.deviceKeyService = deviceKeyService;
    }

    @Transactional(readOnly = true)
    public Result execute(String username) {
        UserDataBase user = userRepository.findByUsername(normalizeUsername(username));
        if (user == null) {
            return Result.userNotFound();
        }

        return Result.generated(deviceKeyService.startAuthenticationChallenge(user));
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    public record Result(Status status, String message, DeviceKeyChallengeResponse challenge) {

        public static Result userNotFound() {
            return new Result(Status.USER_NOT_FOUND, "User not found", null);
        }

        public static Result generated(DeviceKeyChallengeResponse challenge) {
            return new Result(Status.GENERATED, null, challenge);
        }
    }

    public enum Status {
        GENERATED,
        USER_NOT_FOUND
    }
}
