package source.ledger.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import source.ledger.repository.LedgerEntryRepository;
import source.auth.application.service.validation.totp.contracts.TOTPVerifier;
import source.security.VaultKeyProvider;
import source.treasury.domain.model.ReserveSnapshot;
import source.treasury.service.ReserveBalanceService;
import source.treasury.service.TreasuryConfigService;
import source.ledger.dto.TreasuryAuditConfigRequestDTO;
import source.ledger.dto.TreasuryAuditConfigResponseDTO;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ─── AUDITORIA E PROOD OF RESERVES ──────────────────────────────────────────
 *
 * Controlador responsável por exibir a Solvência Matemática (Proof of Reserves)
 * e o mecanismo restrito de Siphoning (Saque de Lucros da Plataforma).
 */
@RestController
@RequestMapping("/v1/audit")
public class LedgerAuditController {

    private static final Logger log = LoggerFactory.getLogger(LedgerAuditController.class);

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    // A carteira MPC Central que detém os saldos "on-chain"
    private static final String POCKET_MPC_ADDRESS = "1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP";

    // O destino FÍSICO DO DONO DA PLATAFORMA.
    // ⚠️ REGRA DE OURO: Esse endereço é Imutável no código. Uma invasão no banco
    // não consegue mudar para onde o lucro é escoado.
    private static final String OWNER_MULTISIG_HARDWARE_ADDRESS = "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh";

    @Autowired
    private TOTPVerifier totpVerifier;

    @Autowired
    private VaultKeyProvider vaultKeyProvider;

    @Autowired
    private ReserveBalanceService reserveBalanceService;

    @Autowired
    private TreasuryConfigService treasuryConfigService;

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

    /**
     * POST /siphon (Tubo de sucção de Taxas)
     * Remove o Fee PENDING e envia pra Multisig Fria.
     * Requer TOTP e Assinatura originária do Yubikey (Simulado via Header).
     */
    @PostMapping("/siphon")
    public ResponseEntity<Map<String, String>> siphonFees(@RequestHeader("X-Owner-TOTP") String totpCode,
            @RequestHeader("X-Hardware-Signature") String hardwareSig,
            @RequestBody Map<String, String> body) {

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

        String requiredHardwareSignature = firstNonBlank(expectedHardwareSignature, "Yubikey");
        if (hardwareSig == null || !hardwareSig.contains(requiredHardwareSignature)) {
            log.warn("[SIPHON] Hardware signature mismatch.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid Hardware Attestation"));
        }

        // Recupera todo o montante disponível
        BigDecimal profitToExtract = ledgerEntryRepository.calculatePlatformProfitPending();

        if (profitToExtract == null || profitToExtract.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "No fees to collect."));
        }

        // Em produção, isso iria assinar a transação "profitToExtract"
        // enviando para OWNER_MULTISIG_HARDWARE_ADDRESS
        log.info("[SIPHON] Executing payout of {} to Immutable Multisig Address {}", profitToExtract,
                OWNER_MULTISIG_HARDWARE_ADDRESS);

        // Atualiza registros para COLLECTED
        ledgerEntryRepository.markFeesAsCollected();

        return ResponseEntity.ok(Map.of(
                "message", "Siphon Succeeded.",
                "amount_withdrawn", profitToExtract.toPlainString(),
                "destination", OWNER_MULTISIG_HARDWARE_ADDRESS));
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
}
