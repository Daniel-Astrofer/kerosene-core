package source.ledger.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import source.ledger.repository.LedgerEntryRepository;
import source.auth.application.service.validation.totp.contracts.TOTPVerifier;
import source.security.VaultKeyProvider;
import source.common.infra.logging.LogSanitizer;
import source.ledger.entity.SiphonRequest;
import source.treasury.domain.model.ReserveSnapshot;
import source.treasury.dto.OperationalReserveProofResponseDTO;
import source.treasury.dto.TreasuryPayoutResponseDTO;
import source.treasury.service.OperationalReserveProofService;
import source.treasury.service.ReserveBalanceService;
import source.treasury.service.TreasuryConfigService;
import source.treasury.service.TreasuryPayoutService;
import source.ledger.dto.TreasuryAuditConfigRequestDTO;
import source.ledger.dto.TreasuryAuditConfigResponseDTO;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ─── AUDITORIA E PROOD OF RESERVES ──────────────────────────────────────────
 *
 * Controlador responsável por exibir a Solvência Matemática (Proof of Reserves)
 * e o mecanismo restrito de Siphoning (Saque de Lucros da Plataforma).
 */
@RestController
@RequestMapping("/v1/audit")
@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")
public class LedgerAuditController {

    private static final Logger log = LoggerFactory.getLogger(LedgerAuditController.class);

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private TOTPVerifier totpVerifier;

    @Autowired
    private VaultKeyProvider vaultKeyProvider;

    @Autowired
    private ReserveBalanceService reserveBalanceService;

    @Autowired
    private TreasuryConfigService treasuryConfigService;

    @Autowired
    private TreasuryPayoutService treasuryPayoutService;

    @Autowired
    private OperationalReserveProofService operationalReserveProofService;

    @Value("${security.admin.attestation-token:}")
    private String adminAttestationToken;

    @Value("${security.founder.totp-secret:}")
    private String founderTotpSecret;

    @Value("${security.owner.hardware-signature:Yubikey}")
    private String expectedHardwareSignature;

    /**
     * GET /stats (Proof of Reserves PÚBLICA)
     * Pode ser consumida pelo Frontend para mostrar Transparência a todos.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getTransparencyStats() {
        BigDecimal liability = ledgerEntryRepository.calculateLiabilityToUsers();
        BigDecimal profit = ledgerEntryRepository.calculatePlatformProfitPending();

        ReserveSnapshot reserves = reserveBalanceService.captureSnapshot();
        BigDecimal actualOnchainBalance = reserves.totalOnchainBtc();

        Map<String, Object> stats = new HashMap<>();
        stats.put("liability_to_users", liability);
        stats.put("platform_profit_pending", profit);
        stats.put("actual_onchain_balance", actualOnchainBalance);
        stats.put("actual_lightning_balance", reserves.lightningBtc());
        stats.put("actual_wallet_xpub_balance", reserves.walletMonitoredOnchainBtc());
        stats.put("actual_treasury_xpub_balance", reserves.treasuryXpubOnchainBtc());
        stats.put("actual_total_assets", reserves.totalAssetsBtc());

        // A regra do Pânico: Se o dinheiro devido for maior que o real existente no
        // blockchain
        if (actualOnchainBalance != null && liability != null) {
            boolean isSolvent = actualOnchainBalance.compareTo(liability) >= 0;
            stats.put("is_solvent", isSolvent);

            if (!isSolvent) {
                // 💥 Em um caso real, aqui deveria invocar o ShutdownHook e travar as escritas
                // de banco
                // (Ocorreu um Hack ou inflação de saldo não pareada em blockchain)
                log.error("[CRITICAL] PLATFORM IS INSOLVENT! ON-CHAIN BALANCE IS LESS THAN USER LIABILITIES!");
            }
        }

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getTreasuryAuditConfig(
            @RequestHeader(value = "X-Admin-Token", required = false) String providedToken) {
        if (!isValidAdminToken(providedToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Invalid admin token."));
        }
        TreasuryAuditConfigResponseDTO response = treasuryConfigService.getGlobalConfigResponse();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateTreasuryAuditConfig(
            @RequestHeader(value = "X-Admin-Token", required = false) String providedToken,
            @RequestBody TreasuryAuditConfigRequestDTO request) {
        if (!isValidAdminToken(providedToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Invalid admin token."));
        }
        TreasuryAuditConfigResponseDTO response = treasuryConfigService.updateGlobalConfig(
                request.maxWithdrawLimit(),
                request.auditXpub());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reserves/operational-proof")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OperationalReserveProofResponseDTO> generateOperationalReserveProof() {
        return ResponseEntity.ok(operationalReserveProofService.generateSnapshot());
    }

    /**
     * POST /siphon mantém compatibilidade operacional, mas não faz settlement
     * manual. Ele cria uma solicitação auditável, aplica step-up e enfileira a
     * execução real via TreasuryPayoutWorker.
     */
    @PostMapping("/siphon")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> siphonFees(@RequestHeader("X-Owner-TOTP") String totpCode,
            @RequestHeader("X-Hardware-Signature") String hardwareSig,
            @RequestBody Map<String, String> body) {

        ResponseEntity<Map<String, String>> stepUpFailure = validateSiphonStepUp(totpCode, hardwareSig);
        if (stepUpFailure != null) {
            return stepUpFailure;
        }

        try {
            SiphonRequest requested = treasuryPayoutService.requestPayout(
                    value(body, "idempotencyKey"),
                    value(body, "requestedBy"),
                    parseOptionalAmount(value(body, "amount")));
            SiphonRequest queued = treasuryPayoutService.approveAndQueue(
                    requested.getId(),
                    value(body, "approvedBy"),
                    approvalReference(totpCode, hardwareSig));
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                    "message", "Treasury payout queued.",
                    "request_id", queued.getId().toString(),
                    "status", queued.getStatus().name(),
                    "amount_withdrawn", queued.getAmount().toPlainString(),
                    "destination", queued.getDestinationAddress()));
        } catch (RuntimeException exception) {
            return mapPayoutException(exception);
        }
    }

    @PostMapping("/siphon/requests")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> requestSiphonPayout(@RequestBody(required = false) Map<String, String> body) {
        try {
            SiphonRequest request = treasuryPayoutService.requestPayout(
                    value(body, "idempotencyKey"),
                    value(body, "requestedBy"),
                    parseOptionalAmount(value(body, "amount")));
            return ResponseEntity.status(HttpStatus.CREATED).body(TreasuryPayoutResponseDTO.from(request));
        } catch (RuntimeException exception) {
            return mapPayoutException(exception);
        }
    }

    @PostMapping("/siphon/requests/{requestId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> approveSiphonPayout(
            @PathVariable UUID requestId,
            @RequestHeader("X-Owner-TOTP") String totpCode,
            @RequestHeader("X-Hardware-Signature") String hardwareSig,
            @RequestBody(required = false) Map<String, String> body) {
        ResponseEntity<Map<String, String>> stepUpFailure = validateSiphonStepUp(totpCode, hardwareSig);
        if (stepUpFailure != null) {
            return stepUpFailure;
        }
        try {
            SiphonRequest queued = treasuryPayoutService.approveAndQueue(
                    requestId,
                    value(body, "approvedBy"),
                    approvalReference(totpCode, hardwareSig));
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(TreasuryPayoutResponseDTO.from(queued));
        } catch (RuntimeException exception) {
            return mapPayoutException(exception);
        }
    }

    @PostMapping("/siphon/requests/{requestId}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> cancelSiphonPayout(
            @PathVariable UUID requestId,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            SiphonRequest cancelled = treasuryPayoutService.cancel(
                    requestId,
                    value(body, "cancelledBy"),
                    value(body, "reason"));
            return ResponseEntity.ok(TreasuryPayoutResponseDTO.from(cancelled));
        } catch (RuntimeException exception) {
            return mapPayoutException(exception);
        }
    }

    private ResponseEntity<Map<String, String>> validateSiphonStepUp(String totpCode, String hardwareSig) {
        String effectiveFounderTotpSecret = firstNonBlank(founderTotpSecret, System.getenv("FOUNDER_TOTP_SECRET"));
        if (effectiveFounderTotpSecret == null) {
            log.error("[SIPHON] Founder TOTP secret is not configured.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Founder TOTP secret not configured."));
        }

        try {
            totpVerifier.totpVerify(effectiveFounderTotpSecret, totpCode);
        } catch (Exception e) {
            log.warn("[SIPHON] INVALID TOTP. Intrusion attempt?");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid TOTP"));
        }

        String requiredHardwareSignature = Objects.requireNonNull(firstNonBlank(expectedHardwareSignature, "Yubikey"));
        if (hardwareSig == null || !hardwareSig.contains(requiredHardwareSignature)) {
            log.warn("[SIPHON] Hardware signature mismatch.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid Hardware Attestation"));
        }

        return null;
    }

    private boolean isValidAdminToken(String provided) {
        if (adminAttestationToken == null || adminAttestationToken.isBlank()) {
            return false;
        }
        if (provided == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                adminAttestationToken.getBytes(StandardCharsets.UTF_8));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String value(Map<String, String> body, String key) {
        if (body == null || key == null) {
            return null;
        }
        return firstNonBlank(body.get(key));
    }

    private BigDecimal parseOptionalAmount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("amount must be a valid BTC decimal value.");
        }
    }

    private String approvalReference(String totpCode, String hardwareSig) {
        return "totp=" + LogSanitizer.fingerprint(totpCode)
                + ":hardware=" + LogSanitizer.fingerprint(hardwareSig);
    }

    private ResponseEntity<Map<String, String>> mapPayoutException(RuntimeException exception) {
        if (exception instanceof IllegalArgumentException) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
        if (exception instanceof IllegalStateException) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", exception.getMessage()));
        }
        log.error("[SIPHON] Treasury payout request failed: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Treasury payout request failed."));
    }
}
