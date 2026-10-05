package com.kerosene.admin.controller;

import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.kerosene.common.dto.ApiResponse;
import com.kerosene.admin.service.AdminLedgerService;
import com.kerosene.platform.security.AdminRoles;

import java.util.List;

/**
 * Exposes administrative read-only views of ledger accounts and journal entries.
 * Every route requires one of the roles defined by {@link AdminRoles}; when the backing service
 * is unavailable, responses use an explicit UNAVAILABLE projection rather than fabricated balances.
 */
@RestController
@RequestMapping("/api/admin/ledger")
@PreAuthorize(AdminRoles.HAS_ANY_ADMIN_ROLE)
public class AdminLedgerController {

    /** Optional operational ledger query service. */
    private final ObjectProvider<AdminLedgerService> adminLedgerService;

    /**
     * Creates the controller with a deferred provider so deployments without ledger integration can start.
     *
     * @param adminLedgerService provider for ledger account and journal queries
     */
    public AdminLedgerController(ObjectProvider<AdminLedgerService> adminLedgerService) {
        this.adminLedgerService = adminLedgerService;
    }

    /** Resolves the configured query service or a typed unavailable-response implementation. */
    private AdminLedgerService require() {
        return adminLedgerService.getIfAvailable(UnavailableAdminLedgerService::new);
    }

    /**
     * Retrieves one ledger account detail by its path identifier.
     *
     * @param id nonblank ledger account identifier
     * @return success envelope containing the account detail or unavailable projection
     */
    @GetMapping("/accounts/{id}")
    public ResponseEntity<ApiResponse<AdminLedgerService.LedgerAccountDetail>> findAccount(
            @PathVariable @NotBlank String id) {
        AdminLedgerService.LedgerAccountDetail account = require().findAccount(id);
        return ResponseEntity.ok(ApiResponse.success("Ledger account retrieved.", account));
    }

    /**
     * Retrieves one ledger journal detail by its path identifier.
     *
     * @param id nonblank journal entry identifier
     * @return success envelope containing the journal detail or unavailable projection
     */
    @GetMapping("/journals/{id}")
    public ResponseEntity<ApiResponse<AdminLedgerService.LedgerJournalDetail>> findJournal(
            @PathVariable @NotBlank String id) {
        AdminLedgerService.LedgerJournalDetail journal = require().findJournal(id);
        return ResponseEntity.ok(ApiResponse.success("Ledger journal entry retrieved.", journal));
    }

    /** Read-only service fallback used when the optional ledger query bean is absent. */
    private static final class UnavailableAdminLedgerService implements AdminLedgerService {

        /**
         * Produces an explicit unavailable account projection with zeroed balances.
         *
         * @param id requested account identifier
         * @return account detail marked UNAVAILABLE
         */
        @Override
        public LedgerAccountDetail findAccount(String id) {
            return new LedgerAccountDetail(id, "unknown", "BTC", "0",
                    "UNAVAILABLE", 0L, 0L, List.of());
        }

        /**
         * Produces an explicit unavailable journal projection without claiming a real posting.
         *
         * @param id requested journal identifier
         * @return journal detail marked UNAVAILABLE
         */
        @Override
        public LedgerJournalDetail findJournal(String id) {
            return new LedgerJournalDetail(id, "unknown", "unknown", "0",
                    "BTC", "ledger unavailable", "unknown", 0L, "UNAVAILABLE");
        }
    }
}
