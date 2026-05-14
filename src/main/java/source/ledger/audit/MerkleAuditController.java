package source.ledger.audit;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for Merkle audit transparency.
 * All endpoints require authentication — the root proves integrity
 * without exposing individual wallet owners.
 */
@RestController
@RequestMapping("/audit")
public class MerkleAuditController {

    private final MerkleAuditService auditService;

    public MerkleAuditController(MerkleAuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * GET /audit/latest-root
     * Returns the most recently computed Merkle root checkpoint.
     * Response body:
     * {
     * "merkleRoot": "<64-char hex>",
     * "ledgerCount": 42,
     * "createdAt": "2026-02-25T16:00:00",
     * "anchorTxid": null
     * }
     */
    @GetMapping("/latest-root")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> latestRoot() {
        return auditService.findLatest()
                .map(c -> ResponseEntity.ok(toMap(c)))
                .orElse(ResponseEntity.ok(Map.of(
                        "merkleRoot", "NO_CHECKPOINT_YET",
                        "ledgerCount", 0,
                        "createdAt", LocalDateTime.now().toString(),
                        "anchorTxid", (Object) null)));
    }

    /**
     * GET /audit/history?limit=10
     * Returns the last N Merkle root checkpoints (max 50), newest first.
     */
    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> history(@RequestParam(defaultValue = "10") int limit) {
        int safeLimit = Math.min(limit, 50);
        List<Map<String, Object>> results = auditService.findHistory(safeLimit)
                .stream()
                .map(this::toMap)
                .toList();
        return ResponseEntity.ok(results);
    }

    /**
     * POST /audit/trigger (admin-only, for manual audit runs)
     * Immediately computes and persists a new Merkle checkpoint.
     */
    @org.springframework.web.bind.annotation.PostMapping("/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> triggerAudit() {
        MerkleAuditEntity checkpoint = auditService.computeAndPersist();
        return ResponseEntity.ok(toMap(checkpoint));
    }

    // ──────────────────────────────────────────────────────────────────────────

    private Map<String, Object> toMap(MerkleAuditEntity c) {
        return Map.of(
                "id", c.getId().toString(),
                "merkleRoot", c.getMerkleRoot(),
                "ledgerCount", c.getLedgerCount(),
                "createdAt", c.getCreatedAt().toString(),
                "anchorTxid", c.getAnchorTxid() != null ? c.getAnchorTxid() : "");
    }
}
