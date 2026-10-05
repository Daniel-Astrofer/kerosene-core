package com.kerosene.admin.controller;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.kerosene.common.dto.ApiResponse;
import com.kerosene.admin.service.AdminOnrampService;
import com.kerosene.platform.security.AdminRoles;

/** Administrative, read-only endpoint for looking up a single onramp order. */
@RestController
@RequestMapping("/api/admin/onramp")
@PreAuthorize(AdminRoles.HAS_ANY_ADMIN_ROLE)
public class AdminOnrampController {

    /** Service that resolves onramp order details from the relevant integration. */
    private final AdminOnrampService adminOnrampService;

    /**
     * Creates the controller with its onramp query service.
     *
     * @param adminOnrampService order lookup service
     */
    public AdminOnrampController(AdminOnrampService adminOnrampService) {
        this.adminOnrampService = adminOnrampService;
    }

    /**
     * Retrieves an administrative order detail by identifier.
     *
     * @param id nonblank provider/KFE order identifier
     * @return success envelope containing the requested order detail
     */
    @GetMapping("/orders/{id}")
    public ResponseEntity<ApiResponse<AdminOnrampService.OnrampOrderDetail>> findOrder(
            @PathVariable @NotBlank String id) {
        AdminOnrampService.OnrampOrderDetail order = adminOnrampService.findOrder(id);
        return ResponseEntity.ok(ApiResponse.success("Onramp order retrieved.", order));
    }
}
