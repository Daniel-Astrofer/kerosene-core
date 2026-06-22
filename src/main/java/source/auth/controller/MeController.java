package source.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import source.auth.application.usecase.me.GetCurrentUserProfileUseCase;
import source.common.dto.ApiResponse;
import source.common.exception.ErrorCodes;

import java.util.Map;

@RestController
@RequestMapping("/auth/me")
public class MeController {

    private final GetCurrentUserProfileUseCase getCurrentUserProfileUseCase;

    public MeController(GetCurrentUserProfileUseCase getCurrentUserProfileUseCase) {
        this.getCurrentUserProfileUseCase = getCurrentUserProfileUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentUser(
            @RequestHeader(value = "X-Device-Hash", required = false) String deviceHash) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        try {
            Long userId = Long.parseLong(auth.getName());
            GetCurrentUserProfileUseCase.Result result = getCurrentUserProfileUseCase.execute(userId, deviceHash);

            if (!result.found()) {
                return ResponseEntity.status(404).body(ApiResponse.error("User not found", ErrorCodes.AUTH_USER_NOT_FOUND));
            }

            return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", result.profile()));
        } catch (NumberFormatException e) {
            return ResponseEntity.status(401).body(ApiResponse.error("Invalid token context", ErrorCodes.AUTH_SESSION_EXPIRED));
        }
    }
}
