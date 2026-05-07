package source.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import source.auth.AuthExceptions;
import source.auth.application.service.admin.AdminAccessService;
import source.auth.dto.AdminAccessAttemptDTO;
import source.auth.dto.AdminAccessDecisionRequestDTO;
import source.auth.dto.AdminAuthenticatedDeviceDTO;
import source.auth.dto.AdminKeyCreateRequestDTO;
import source.auth.dto.AdminKeyStatusDTO;
import source.auth.dto.AdminLoginRequestDTO;
import source.auth.dto.AdminLoginResponseDTO;
import source.auth.model.enums.AdminAccessDeviceStatus;
import source.common.dto.ApiResponse;

@RestController
@RequestMapping("/auth/admin")
public class AdminAccessController {

    private final AdminAccessService adminAccessService;

    public AdminAccessController(AdminAccessService adminAccessService) {
        this.adminAccessService = adminAccessService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AdminLoginResponseDTO>> startLogin(
            @RequestBody AdminLoginRequestDTO request,
            HttpServletRequest httpRequest) {
        try {
            AdminLoginResponseDTO response = adminAccessService.startLogin(
                    request,
                    clientAddress(httpRequest),
                    httpRequest.getHeader("User-Agent"));
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(ApiResponse.success("Admin login pending mobile approval.", response));
        } catch (AuthExceptions.StructuredAuthException exception) {
            return ResponseEntity.status(exception.getStatus())
                    .body(ApiResponse.error(exception.getMessage(), exception.getErrorCode(), null));
        }
    }

    @GetMapping("/login/{attemptId}")
    public ResponseEntity<ApiResponse<AdminLoginResponseDTO>> pollLogin(@PathVariable UUID attemptId) {
        try {
            AdminLoginResponseDTO response = adminAccessService.pollLogin(attemptId);
            HttpStatus status = response.requiresMobileApproval() ? HttpStatus.ACCEPTED : HttpStatus.OK;
            return ResponseEntity.status(status).body(ApiResponse.success(response.message(), response));
        } catch (AuthExceptions.StructuredAuthException exception) {
            return ResponseEntity.status(exception.getStatus())
                    .body(ApiResponse.error(exception.getMessage(), exception.getErrorCode(), null));
        }
    }

    @PostMapping("/key")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminKeyStatusDTO>> createOrRotateKey(
            @RequestBody AdminKeyCreateRequestDTO request) {
        AdminKeyStatusDTO response = adminAccessService.createOrRotateKey(authenticatedUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Admin key configured.", response));
    }

    @GetMapping("/key")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminKeyStatusDTO>> keyStatus() {
        return ResponseEntity.ok(ApiResponse.success(
                "Admin key status retrieved.",
                adminAccessService.keyStatus(authenticatedUserId())));
    }

    @DeleteMapping("/key")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminKeyStatusDTO>> revokeKey() {
        return ResponseEntity.ok(ApiResponse.success(
                "Admin key revoked.",
                adminAccessService.revokeKey(authenticatedUserId())));
    }

    @GetMapping("/access-attempts/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AdminAccessAttemptDTO>>> pendingAttempts() {
        return ResponseEntity.ok(ApiResponse.success(
                "Pending admin access attempts retrieved.",
                adminAccessService.pendingAttempts(authenticatedUserId())));
    }

    @PostMapping("/access-attempts/{attemptId}/decision")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminAccessAttemptDTO>> decide(
            @PathVariable UUID attemptId,
            @RequestBody AdminAccessDecisionRequestDTO request) {
        AdminAccessAttemptDTO response = adminAccessService.decide(
                authenticatedUserId(),
                attemptId,
                request.getDecision());
        return ResponseEntity.ok(ApiResponse.success("Admin access decision registered.", response));
    }

    @GetMapping("/devices")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AdminAuthenticatedDeviceDTO>>> devices() {
        return ResponseEntity.ok(ApiResponse.success(
                "Admin devices retrieved.",
                adminAccessService.devices(authenticatedUserId())));
    }

    @PostMapping("/devices/{deviceId}/block")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminAuthenticatedDeviceDTO>> blockDevice(@PathVariable String deviceId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Admin device blocked.",
                adminAccessService.changeDeviceStatus(
                        authenticatedUserId(),
                        deviceId,
                        AdminAccessDeviceStatus.BLOCKED)));
    }

    @PostMapping("/devices/{deviceId}/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminAuthenticatedDeviceDTO>> revokeDevice(@PathVariable String deviceId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Admin device revoked.",
                adminAccessService.changeDeviceStatus(
                        authenticatedUserId(),
                        deviceId,
                        AdminAccessDeviceStatus.REVOKED)));
    }

    private Long authenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            throw new AuthExceptions.InvalidCredentials("Not authenticated.");
        }
        return Long.parseLong(auth.getName());
    }

    private String clientAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
