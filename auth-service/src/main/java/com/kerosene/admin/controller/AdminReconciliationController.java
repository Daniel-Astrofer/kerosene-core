package com.kerosene.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.kerosene.common.dto.ApiResponse;
import com.kerosene.admin.service.AdminReconciliationService;
import com.kerosene.platform.security.AdminRoles;

/** Exposes administrative reconciliation status for finance operators. */
@RestController
@RequestMapping("/api/admin/reconciliation")
@PreAuthorize(AdminRoles.HAS_ANY_ADMIN_ROLE)
public class AdminReconciliationController {

    /** Service aggregating current reconciliation progress and health. */
    private final AdminReconciliationService adminReconciliationService;

    /**
     * Creates the controller with its reconciliation status service.
     *
     * @param adminReconciliationService reconciliation status use case
     */
    public AdminReconciliationController(AdminReconciliationService adminReconciliationService) {
        this.adminReconciliationService = adminReconciliationService;
    }

    /**
     * Returns current reconciliation status to an authorized administrator.
     *
     * @return successful API envelope containing reconciliation state
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<AdminReconciliationService.ReconciliationStatus>> status() {
        AdminReconciliationService.ReconciliationStatus status = adminReconciliationService.status();
        return ResponseEntity.ok(ApiResponse.success("Reconciliation status retrieved.", status));
    }
}
