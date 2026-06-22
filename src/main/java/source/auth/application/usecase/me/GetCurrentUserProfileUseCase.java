package source.auth.application.usecase.me;

import org.springframework.stereotype.Component;
import source.auth.application.service.account.AppPinService;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.model.entity.UserDataBase;
import source.auth.model.enums.UserRole;

import java.util.HashMap;
import java.util.Map;

@Component
public class GetCurrentUserProfileUseCase {

    private final UserServiceContract userServiceContract;
    private final AppPinService appPinService;

    public GetCurrentUserProfileUseCase(UserServiceContract userServiceContract, AppPinService appPinService) {
        this.userServiceContract = userServiceContract;
        this.appPinService = appPinService;
    }

    public Result execute(Long userId, String deviceHash) {
        return userServiceContract.buscarPorId(userId)
                .map(user -> Result.found(profileFor(user, deviceHash)))
                .orElseGet(Result::notFound);
    }

    private Map<String, Object> profileFor(UserDataBase user, String deviceHash) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", String.valueOf(user.getId()));
        response.put("userId", String.valueOf(user.getId()));
        response.put("username", user.getUsername());
        response.put("role", user.getRole().name());
        response.put("isAdmin", user.getRole() == UserRole.ADMIN);
        response.put("testBalanceClaimed", Boolean.TRUE.equals(user.getTestBalanceClaimed()));
        response.put("passkeyEnabledForTransactions", Boolean.TRUE.equals(user.getPasskeyEnabledForTransactions()));
        response.put("appPinEnabled", appPinService.getStatus(user, deviceHash).enabled());

        if (user.getCreatedAt() != null) {
            response.put("createdAt", user.getCreatedAt().toString());
        }

        return response;
    }

    public record Result(boolean found, Map<String, Object> profile) {

        private static Result found(Map<String, Object> profile) {
            return new Result(true, profile);
        }

        private static Result notFound() {
            return new Result(false, null);
        }
    }
}
