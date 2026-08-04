package com.kerosene.common.admin;

import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.kerosene.common.dto.ApiResponse;
import com.kerosene.common.security.AdminRoles;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ledger")
@PreAuthorize(AdminRoles.HAS_ANY_ADMIN_ROLE)
public class AdminLedgerController {

    private final ObjectProvider<AdminLedgerService> adminLedgerService;

    public AdminLedgerController(ObjectProvider<AdminLedgerService> adminLedgerService) {
        this.adminLedgerService = adminLedgerService;
    }

    private AdminLedgerService require() {
        return adminLedgerService.getIfAvailable(() ->
                new AdminLedgerService() {
                    @Override
                    public LedgerAccountDetail findAccount(String id) {
                        return new LedgerAccountDetail(id, "unknown", "BTC", "0",
                                "UNAVAILABLE", 0L, 0L, List.of());
                    }
                    @Override
                    public LedgerJournalDetail findJournal(String id) {
                        return new LedgerJournalDetail(id, "unknown", "unknown", "0",
                                "BTC", "ledger unavailable", "unknown", 0L, "UNAVAILABLE");
                    }
                });
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<ApiResponse<AdminLedgerService.LedgerAccountDetail>> findAccount(
            @PathVariable @NotBlank String id) {
        AdminLedgerService.LedgerAccountDetail account = require().findAccount(id);
        return ResponseEntity.ok(ApiResponse.success("Ledger account retrieved.", account));
    }

    @GetMapping("/journals/{id}")
    public ResponseEntity<ApiResponse<AdminLedgerService.LedgerJournalDetail>> findJournal(
            @PathVariable @NotBlank String id) {
        AdminLedgerService.LedgerJournalDetail journal = require().findJournal(id);
        return ResponseEntity.ok(ApiResponse.success("Ledger journal entry retrieved.", journal));
    }
}
