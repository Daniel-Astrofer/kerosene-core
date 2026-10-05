package com.kerosene.admin.controller;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.kerosene.common.dto.ApiResponse;
import com.kerosene.admin.service.AdminProviderService;

/** Exposes an ADMIN-only provider connection validation result. */
@RestController
@RequestMapping("/api/admin/providers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProviderController {

    /** Provider integration service that performs a connection validation. */
    private final AdminProviderService adminProviderService;

    /**
     * Creates the controller with its provider validation service.
     *
     * @param adminProviderService provider validation use case
     */
    public AdminProviderController(AdminProviderService adminProviderService) {
        this.adminProviderService = adminProviderService;
    }

    /**
     * Validates a configured provider connection selected by its identifier.
     *
     * @param id nonblank provider connection identifier
     * @return successful API envelope with the validation outcome
     */
    @GetMapping("/connections/{id}/validation")
    public ResponseEntity<ApiResponse<AdminProviderService.ProviderValidationResult>> validateConnection(
            @PathVariable @NotBlank String id) {
        AdminProviderService.ProviderValidationResult result = adminProviderService.validateConnection(id);
        return ResponseEntity.ok(ApiResponse.success("Provider connection validation result.", result));
    }
}
