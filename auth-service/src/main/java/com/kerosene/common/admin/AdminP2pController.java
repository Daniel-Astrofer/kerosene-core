package com.kerosene.common.admin;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.kerosene.common.dto.ApiResponse;
import com.kerosene.common.security.AdminRoles;

@RestController
@RequestMapping("/api/admin/p2p")
@PreAuthorize(AdminRoles.HAS_ANY_ADMIN_ROLE)
public class AdminP2pController {

    private final AdminP2pService adminP2pService;

    public AdminP2pController(AdminP2pService adminP2pService) {
        this.adminP2pService = adminP2pService;
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<ApiResponse<AdminP2pService.P2pOrderDetail>> findOrder(
            @PathVariable @NotBlank String id) {
        AdminP2pService.P2pOrderDetail order = adminP2pService.findOrder(id);
        return ResponseEntity.ok(ApiResponse.success("P2P order retrieved.", order));
    }
}
