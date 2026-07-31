package com.kerosene.common.admin;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.kerosene.common.dto.ApiResponse;

@RestController
@RequestMapping("/api/admin/providers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProviderController {

    private final AdminProviderService adminProviderService;

    public AdminProviderController(AdminProviderService adminProviderService) {
        this.adminProviderService = adminProviderService;
    }

    @GetMapping("/connections/{id}/validation")
    public ResponseEntity<ApiResponse<AdminProviderService.ProviderValidationResult>> validateConnection(
            @PathVariable @NotBlank String id) {
        AdminProviderService.ProviderValidationResult result = adminProviderService.validateConnection(id);
        return ResponseEntity.ok(ApiResponse.success("Provider connection validation result.", result));
    }
}
