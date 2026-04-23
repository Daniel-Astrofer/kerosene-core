package source.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.model.entity.UserDataBase;
import source.common.dto.ApiResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth/me")
public class MeController {

    private final UserServiceContract userServiceContract;

    public MeController(UserServiceContract userServiceContract) {
        this.userServiceContract = userServiceContract;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        try {
            Long userId = Long.parseLong(auth.getName());
            Optional<UserDataBase> userOpt = userServiceContract.buscarPorId(userId);

            if (userOpt.isEmpty()) {
                return ResponseEntity.status(404).body(ApiResponse.error("User not found", "USER_NOT_FOUND"));
            }

            UserDataBase user = userOpt.get();
            Map<String, Object> response = new HashMap<>();
            response.put("id", String.valueOf(user.getId()));
            response.put("userId", String.valueOf(user.getId()));
            response.put("username", user.getUsername());
            response.put("testBalanceClaimed", Boolean.TRUE.equals(user.getTestBalanceClaimed()));
            response.put("passkeyEnabledForTransactions", Boolean.TRUE.equals(user.getPasskeyEnabledForTransactions()));
            
            if (user.getCreatedAt() != null) {
                response.put("createdAt", user.getCreatedAt().toString());
            }

            return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", response));
            
        } catch (NumberFormatException e) {
            return ResponseEntity.status(401).body(ApiResponse.error("Invalid token context", "UNAUTHORIZED"));
        }
    }
}
