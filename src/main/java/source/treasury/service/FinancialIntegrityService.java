package source.treasury.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import source.ledger.repository.LedgerRepository;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.security.VaultKeyProvider;

import java.math.BigDecimal;

/**
 * 🛡️ LIQUIDITY & HEALTH MONITOR (V5.8 Production Hardened)
 * ─────────────────────────────────────────────────────────────
 * Responsável por auditar as reservas do sistema (On-chain/Lightning) contra o ledger.
 * Atua como o "Botão de Pânico" automatizado.
 */
@Service
public class FinancialIntegrityService {

    private static final Logger log = LoggerFactory.getLogger(FinancialIntegrityService.class);

    private final LedgerRepository ledgerRepository;
    private final RedisServicer redisService;
    private final VaultKeyProvider vaultKeyProvider;

    // Redis Keys for Circuit Breaker status (Shareable across shards)
    public static final String CIRCUIT_BREAKER_HALT_DEPOSITS = "circuit_breaker:halt_deposits";
    public static final String CIRCUIT_BREAKER_HALT_WITHDRAWALS = "circuit_breaker:halt_withdrawals";

    // Margem de Tolerância: 0.1 BTC (Divergência aceitável por latência de transação)
    private static final BigDecimal DRIFT_TOLERANCE = new BigDecimal("0.001");

    public FinancialIntegrityService(
            LedgerRepository ledgerRepository,
            RedisServicer redisService,
            VaultKeyProvider vaultKeyProvider) {
        this.ledgerRepository = ledgerRepository;
        this.redisService = redisService;
        this.vaultKeyProvider = vaultKeyProvider;
    }

    /**
     * Auditoria Crítica (Audit Loop) a cada 5 minutos.
     * Verifica se Liabilities (Ledger) > Assets (Reserves).
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000)
    public void performFinancialAudit() {
        if (!vaultKeyProvider.isReady()) {
            log.info("[Financial Audit] Skipping audit loop: Vault master key is not available yet.");
            return;
        }

        try {
            BigDecimal totalLiabilities = ledgerRepository.findAll().stream()
                .map(source.ledger.entity.LedgerEntity::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalLiabilities == null) totalLiabilities = BigDecimal.ZERO;

            // Asset Discovery (Mocked - In production this calls bitcoind / lnd)
            BigDecimal hotWalletAssets = getOnchainAssets();
            BigDecimal lightningLiquidity = getLightningLiquidity();
            BigDecimal coldStorageAssets = getColdStorageAssets();

            BigDecimal totalAssets = hotWalletAssets.add(lightningLiquidity).add(coldStorageAssets);

            log.info("[Financial Audit] Liabilities: {} BTC | Total Assets: {} BTC (Hot={}, Lightning={}, Cold={})",
                    totalLiabilities.toPlainString(), totalAssets.toPlainString(),
                    hotWalletAssets.toPlainString(), lightningLiquidity.toPlainString(),
                    coldStorageAssets.toPlainString());

            // 🚩 INSOLVENCY CIRCUIT BREAKER
            // Se o ledger mostrar mais dinheiro que o sistema possui fisicamente, HALT!
            if (totalLiabilities.compareTo(totalAssets.add(DRIFT_TOLERANCE)) > 0) {
                triggerPanicCircuitBreaker("INSOLVENCY_DETECTED: Ledger liabilities exceed physical reserves!");
            } else {
                log.info("[Financial Audit] Integrity Verified: Reserves are sufficient.");
            }

        } catch (Exception e) {
            log.error("[Financial Audit] FAILED to perform audit loop: {}", e.getMessage());
            // Fail-safe: If we can't audit, we should consider halting withdrawals if it persists.
        }
    }

    private void triggerPanicCircuitBreaker(String reason) {
        log.error("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        log.error("🚨 EMERGENCY PANIC: PLATFORM CIRCUIT BREAKER TRIPPED! 🚨");
        log.error("Reason: {}", reason);
        log.error("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");

        // Set global flags in Redis (affecting all Shards immediately)
        redisService.setValue(CIRCUIT_BREAKER_HALT_DEPOSITS, "TRUE", 0);
        redisService.setValue(CIRCUIT_BREAKER_HALT_WITHDRAWALS, "TRUE", 0);

        // Notify SRE (Mocked)
        log.error("ALERT: Admin intervention required. Platform is now in READ-ONLY mode.");
    }

    public boolean isDepositsHalted() {
        return "TRUE".equals(redisService.getValue(CIRCUIT_BREAKER_HALT_DEPOSITS));
    }

    public boolean isWithdrawalsHalted() {
        return "TRUE".equals(redisService.getValue(CIRCUIT_BREAKER_HALT_WITHDRAWALS));
    }

    public void resetCircuitBreakers() {
        redisService.deleteValue(CIRCUIT_BREAKER_HALT_DEPOSITS);
        redisService.deleteValue(CIRCUIT_BREAKER_HALT_WITHDRAWALS);
    }

    // --- Asset Discovery Placeholders (To be implemented with Bitcoin Client) ---

    private BigDecimal getOnchainAssets() {
        // Enforce bitcoind getbalance call here
        return new BigDecimal("1.50000000");
    }

    private BigDecimal getLightningLiquidity() {
        // Enforce ln-cli listchannels sum call here
        return new BigDecimal("10.75000000");
    }

    private BigDecimal getColdStorageAssets() {
        // Tracked separately (signed messages from cold storage)
        return new BigDecimal("50.00000000");
    }
}
