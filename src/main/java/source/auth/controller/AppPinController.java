package source.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import source.auth.AuthExceptions;
import source.auth.application.service.account.AppPinService;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.dto.AppPinStatusDTO;
import source.auth.dto.ConfigureAppPinRequestDTO;
import source.auth.dto.VerifyAppPinRequestDTO;
import source.auth.model.entity.UserDataBase;
import source.common.dto.ApiResponse;

@RestController
@RequestMapping("/auth/security/app-pin")
public class AppPinController {

    private final UserServiceContract userService;
    private final AppPinService appPinService;

    public AppPinController(UserServiceContract userService, AppPinService appPinService) {
        this.userService = userService;
        this.appPinService = appPinService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AppPinStatusDTO>> getStatus(
            @RequestHeader(value = "X-Device-Hash", required = false) String deviceHash) {
        UserDataBase user = getAuthenticatedUser();
        AppPinStatusDTO status = appPinService.getStatus(user, deviceHash);
        return ResponseEntity.ok(ApiResponse.success("App PIN status retrieved successfully.", status));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<AppPinStatusDTO>> configure(
            @RequestHeader(value = "X-Device-Hash", required = false) String deviceHash,
            @RequestBody ConfigureAppPinRequestDTO request) {
        UserDataBase user = getAuthenticatedUser();
        AppPinStatusDTO status = appPinService.configure(user, deviceHash, request);
        return ResponseEntity.ok(ApiResponse.success("App PIN settings updated successfully.", status));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<AppPinStatusDTO>> verify(
            @RequestHeader(value = "X-Device-Hash", required = false) String deviceHash,
            @RequestBody VerifyAppPinRequestDTO request) {
        UserDataBase user = getAuthenticatedUser();
        AppPinStatusDTO status = appPinService.verify(user, deviceHash, request.getPin());
        return ResponseEntity.ok(ApiResponse.success("App PIN verified successfully.", status));
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
