package source.auth.application.usecase.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import source.auth.AuthExceptions;
import source.auth.application.service.account.AppPinService;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.dto.AppPinStatusDTO;
import source.auth.dto.ConfigureAppPinRequestDTO;
import source.auth.dto.VerifyAppPinRequestDTO;
import source.auth.model.entity.UserDataBase;

@Component
public class AppPinOperationsUseCase {

    private final UserServiceContract userService;
    private final AppPinService appPinService;

    public AppPinOperationsUseCase(UserServiceContract userService, AppPinService appPinService) {
        this.userService = userService;
        this.appPinService = appPinService;
    }

    public AppPinStatusDTO getStatus(String deviceHash) {
        return appPinService.getStatus(getAuthenticatedUser(), deviceHash);
    }

    public AppPinStatusDTO configure(String deviceHash, ConfigureAppPinRequestDTO request) {
        return appPinService.configure(getAuthenticatedUser(), deviceHash, request);
    }

    public AppPinStatusDTO verify(String deviceHash, VerifyAppPinRequestDTO request) {
        return appPinService.verify(getAuthenticatedUser(), deviceHash, request.getPin());
    }

    private UserDataBase getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AuthExceptions.InvalidCredentials("Not authenticated.");
        }

        try {
            Long userId = Long.parseLong(auth.getName());
            return userService.buscarPorId(userId)
                    .orElseThrow(() -> new AuthExceptions.InvalidCredentials("Authenticated user not found."));
        } catch (NumberFormatException e) {
            throw new AuthExceptions.InvalidCredentials("Invalid authentication context.");
        }
    }
}
