package com.kerosene.admin.controller;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.kerosene.common.dto.ApiResponse;
import com.kerosene.admin.service.AdminP2pService;
import com.kerosene.platform.security.AdminRoles;

/** Provides role-protected administrative lookup for a P2P order. */
@RestController
@RequestMapping("/api/admin/p2p")
@PreAuthorize(AdminRoles.HAS_ANY_ADMIN_ROLE)
public class AdminP2pController {

    /** P2P order query service. */
    private final AdminP2pService adminP2pService;

    /**
     * Creates the controller with its P2P order query service.
     *
     * @param adminP2pService order detail provider
     */
    public AdminP2pController(AdminP2pService adminP2pService) {
        this.adminP2pService = adminP2pService;
    }

    /**
     * Retrieves order details by a nonblank order identifier.
     *
     * @param id P2P order identifier from the route
     * @return successful API envelope containing the order detail
     */
    @GetMapping("/orders/{id}")
    public ResponseEntity<ApiResponse<AdminP2pService.P2pOrderDetail>> findOrder(
            @PathVariable @NotBlank String id) {
        AdminP2pService.P2pOrderDetail order = adminP2pService.findOrder(id);
        return ResponseEntity.ok(ApiResponse.success("P2P order retrieved.", order));
    }
}
