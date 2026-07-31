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
@RequestMapping("/api/admin/onramp")
@PreAuthorize(AdminRoles.HAS_ANY_ADMIN_ROLE)
public class AdminOnrampController {

    private final AdminOnrampService adminOnrampService;

    public AdminOnrampController(AdminOnrampService adminOnrampService) {
        this.adminOnrampService = adminOnrampService;
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<ApiResponse<AdminOnrampService.OnrampOrderDetail>> findOrder(
            @PathVariable @NotBlank String id) {
        AdminOnrampService.OnrampOrderDetail order = adminOnrampService.findOrder(id);
        return ResponseEntity.ok(ApiResponse.success("Onramp order retrieved.", order));
    }
}
