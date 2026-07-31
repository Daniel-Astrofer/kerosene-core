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
@RequestMapping("/api/admin/ledger")
@PreAuthorize(AdminRoles.HAS_ANY_ADMIN_ROLE)
public class AdminLedgerController {

    private final AdminLedgerService adminLedgerService;

    public AdminLedgerController(AdminLedgerService adminLedgerService) {
        this.adminLedgerService = adminLedgerService;
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<ApiResponse<AdminLedgerService.LedgerAccountDetail>> findAccount(
            @PathVariable @NotBlank String id) {
        AdminLedgerService.LedgerAccountDetail account = adminLedgerService.findAccount(id);
        return ResponseEntity.ok(ApiResponse.success("Ledger account retrieved.", account));
    }

    @GetMapping("/journals/{id}")
    public ResponseEntity<ApiResponse<AdminLedgerService.LedgerJournalDetail>> findJournal(
            @PathVariable @NotBlank String id) {
        AdminLedgerService.LedgerJournalDetail journal = adminLedgerService.findJournal(id);
        return ResponseEntity.ok(ApiResponse.success("Ledger journal entry retrieved.", journal));
    }
}
