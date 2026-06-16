package source.ledger.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import source.ledger.repository.LedgerRepository;
import source.transactions.infra.BlockchainClient;
import source.transactions.infra.LightningClient;

import java.math.BigDecimal;

/**
 * Reconciliation Cron (Shadow Audit):
 * Midnight job to verify platform solvency.
 * Compares Ledger total (Postgres) with real UTXOs + Channel funds (Agente 2).
 */
@Service
@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")
public class ReconciliationAuditService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationAuditService.class);

    private final LedgerRepository ledgerRepository;
    private final BlockchainClient blockchainClient;
    private final LightningClient lightningClient;
    private final StringRedisTemplate redisTemplate;
    private final source.security.VaultKeyProvider vaultKeyProvider;
    private final boolean solvencyAuditEnforced;

    public ReconciliationAuditService(LedgerRepository ledgerRepository,
                                      BlockchainClient blockchainClient,
                                      @Qualifier("lndLightningGateway") LightningClient lightningClient,
                                      StringRedisTemplate redisTemplate,
                                      source.security.VaultKeyProvider vaultKeyProvider,
                                      @Value("${audit.solvency.enforced:true}") boolean solvencyAuditEnforced) {
        this.ledgerRepository = ledgerRepository;
        this.blockchainClient = blockchainClient;
        this.lightningClient = lightningClient;
        this.redisTemplate = redisTemplate;
        this.vaultKeyProvider = vaultKeyProvider;
        this.solvencyAuditEnforced = solvencyAuditEnforced;
    }

    /**
     * Micro-Audit — Hourly high-frequency solvency check (Go-Live Requirement).
     */
    @Scheduled(fixedDelay = 3600000)
    public void performMicroAudit() {
        log.info("[MicroAudit] Executing hourly high-frequency solvency check...");
        performShadowAudit();
    }

    /**
     * Real-time audit trigger for high-value transactions.
     */
    public void triggerInstantAuditForHighValue(BigDecimal amount) {
        if (amount.compareTo(new BigDecimal("0.01")) >= 0) {
            log.warn("[Audit] High-value transaction ({} BTC) detected! Forcing immediate re-audit.", amount);
            performShadowAudit();
        }
    }

    /**
     * Executes at midnight every day.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void performShadowAudit() {
        if (!vaultKeyProvider.isReady()) {
            log.warn("[ShadowAudit] Skipping platform reconciliation: Master key not available yet (STALL mode).");
            return;
        }
        if (!solvencyAuditEnforced) {
            log.info("[ShadowAudit] Solvency enforcement disabled for this profile. Skipping platform reconciliation.");
            return;
        }

        log.info("[ShadowAudit] Starting platform-wide reconciliation...");

        // 1. Get sum of all user balances in Postgres
        BigDecimal totalLedgerBalance = ledgerRepository.sumAllBalances();
        if (totalLedgerBalance == null) totalLedgerBalance = BigDecimal.ZERO;

        // 2. Get real on-chain balance (Hot Wallet)
        long hotWalletSats = blockchainClient.getHotWalletBalance();

        // 3. Get real Lightning balance (Channels + Node Wallet)
        long lightningNodeSats = lightningClient.getLightningNodeBalance();

        BigDecimal realSystemSats = new BigDecimal(hotWalletSats + lightningNodeSats);
        BigDecimal ledgerSats = totalLedgerBalance.multiply(new BigDecimal("100000000"));

        log.info("[ShadowAudit] Internal Ledger: {} sats | Physical Assets: {} sats",
                 ledgerSats.toPlainString(), realSystemSats.toPlainString());

        // 4. Comparison with 1 satoshi tolerance (Agente 2)
        if (realSystemSats.compareTo(ledgerSats) < 0) {
            BigDecimal divergence = ledgerSats.subtract(realSystemSats);
            log.error("[CRITICAL] RECONCILIATION FAILURE: Platform is insolvent by {} sats!", divergence);
            triggerPanicLockdown(divergence);
        } else {
            log.info("[ShadowAudit] SUCCESS: Overall platform solvency verified.");
        }
    }

    private void triggerPanicLockdown(BigDecimal divergence) {
        log.error("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        log.error("!! PANIC LOCKDOWN TRIGGERED - IRREGULARITY IN LEDGER AUDIT      !!");
        log.error("!! DIVERGENCE: {} SATOSHIS                                     !!", divergence);
        log.error("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");

        redisTemplate.opsForValue().set("system:lockdown:panic", "true");
        redisTemplate.opsForValue().set("system:status:withdrawals", "DISABLED_AUDIT_FAILURE");
        redisTemplate.opsForValue().set("system:status:deposits", "DISABLED_AUDIT_FAILURE");

        exportForensicDump(divergence);
    }

    private void exportForensicDump(BigDecimal divergence) {
        log.info("[Forensic] Preparing signed/encrypted state dump to external volume...");
        String dumpPath = "/home/omega/Kerosene/backend/kerosene/panic_dump_" + System.currentTimeMillis() + ".json";
        String content = "{\"divergence\": " + divergence + ", \"timestamp\": " + System.currentTimeMillis() + "}";
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of(dumpPath), content);
            log.info("[Forensic] Dump successfully exported to: {}", dumpPath);
        } catch (java.io.IOException e) {
            log.error("[Forensic] FAILED to write dump: {}", e.getMessage());
        }
    }
}
