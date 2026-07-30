package com.kerosene.common.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.kerosene.common.dto.ApiResponse;
import com.kerosene.common.security.AdminRoles;

@RestController
@RequestMapping("/api/admin/reconciliation")
@PreAuthorize(AdminRoles.HAS_ANY_ADMIN_ROLE)
public class AdminReconciliationController {

    private final AdminReconciliationService adminReconciliationService;

    public AdminReconciliationController(AdminReconciliationService adminReconciliationService) {
        this.adminReconciliationService = adminReconciliationService;
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<AdminReconciliationService.ReconciliationStatus>> status() {
        AdminReconciliationService.ReconciliationStatus status = adminReconciliationService.status();
        return ResponseEntity.ok(ApiResponse.success("Reconciliation status retrieved.", status));
    }
}
