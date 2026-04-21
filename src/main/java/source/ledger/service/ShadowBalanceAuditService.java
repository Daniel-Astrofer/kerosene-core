package source.ledger.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import source.ledger.application.balance.LedgerActiveUserPort;
import source.ledger.repository.LedgerRepository;
import source.treasury.domain.model.ReserveSnapshot;
import source.treasury.service.ReserveBalanceService;

import java.math.BigDecimal;

/**
 * Agente de Auditoria de Shadow Balance (Prevenção contra Quebra Fracionária).
 * Assegura que o total de fundos emitidos no DB (L2) não excede os satoshis reais do nó (L1).
 */
@Service
public class ShadowBalanceAuditService {

    private static final int AUDIT_BATCH_SIZE = 500;

    private final LedgerRepository ledgerRepository;
    private final ReserveBalanceService reserveBalanceService;
    private final LedgerContract ledgerService;
    private final LedgerActiveUserPort activeUserPort;
    private final source.security.VaultKeyProvider vaultKeyProvider;
    private final boolean solvencyAuditEnforced;

    private static final Logger log = LoggerFactory.getLogger(ShadowBalanceAuditService.class);

    public ShadowBalanceAuditService(LedgerRepository ledgerRepository,
                                     ReserveBalanceService reserveBalanceService,
                                     LedgerContract ledgerService,
                                     LedgerActiveUserPort activeUserPort,
                                     source.security.VaultKeyProvider vaultKeyProvider,
                                     @Value("${audit.solvency.enforced:true}") boolean solvencyAuditEnforced) {
        this.ledgerRepository = ledgerRepository;
        this.reserveBalanceService = reserveBalanceService;
        this.ledgerService = ledgerService;
        this.activeUserPort = activeUserPort;
        this.vaultKeyProvider = vaultKeyProvider;
        this.solvencyAuditEnforced = solvencyAuditEnforced;
    }

    /**
     * Executes periodically (e.g. every 10 minutes) to compare total DB wallets balance
     * vs the actual Node hot wallet balance, AND verify internal consistency for all active users.
     */
    @Scheduled(fixedRate = 600000)
    public void auditShadowBalance() {
        if (!vaultKeyProvider.isReady()) {
            log.warn("[ShadowAudit] Skipping audit: Master key not available yet (STALL mode).");
            return;
        }

        log.info("[ShadowAudit] Iniciando auditoria global e interna...");

        // 1. Auditoria de Solvência Global (L1 vs L2)
        if (solvencyAuditEnforced) {
            // Sum in paged batches to avoid loading the whole ledger table into memory.
            BigDecimal totalDbBalance = sumLedgerBalances();

            if (totalDbBalance == null) {
                totalDbBalance = BigDecimal.ZERO;
            }

            long totalSatsDb = totalDbBalance.multiply(new BigDecimal("100000000")).longValue();
            ReserveSnapshot reserves = reserveBalanceService.captureSnapshot();
            long totalReserveSats = reserves.totalAssetsSats();

            if (totalSatsDb > totalReserveSats) {
                log.error("[CRÍTICA] INSOLVÊNCIA DETECTADA! DB={} sats > Reservas={} sats (Hot={} | WalletXPUB={} | TreasuryXPUB={} | Lightning={}).",
                        totalSatsDb,
                        totalReserveSats,
                        reserves.hotWalletBtc().toPlainString(),
                        reserves.walletMonitoredOnchainBtc().toPlainString(),
                        reserves.treasuryXpubOnchainBtc().toPlainString(),
                        reserves.lightningBtc().toPlainString());
                triggerFraudCircuitBreaker();
            }
        } else {
            log.info("[ShadowAudit] Solvency enforcement disabled for this profile. Skipping reserve-vs-ledger comparison.");
        }

        // 2. Auditoria de Integridade Interna (Auditore das Sombras)
        // Varre todos os usuários ativos para garantir que o saldo bate com o extrato e HMAC
        activeUserPort.listActiveUsers().forEach(user -> {
            try {
                ledgerService.validateUserLedgerIntegrity(user.userId());
            } catch (SecurityException e) {
                log.error("[ShadowAudit] Falha de integridade crítica para usuário {}: {}",
                        user.username(),
                        e.getMessage());
            }
        });

        log.info("[ShadowAudit] Auditoria concluída.");
    }

    private BigDecimal sumLedgerBalances() {
        BigDecimal total = BigDecimal.ZERO;
        int pageNumber = 0;
        Page<source.ledger.entity.LedgerEntity> page;

        do {
            page = ledgerRepository.findAll(PageRequest.of(pageNumber++, AUDIT_BATCH_SIZE));
            for (source.ledger.entity.LedgerEntity ledger : page.getContent()) {
                if (ledger.getBalance() != null) {
                    total = total.add(ledger.getBalance());
                }
            }
        } while (page.hasNext());

        return total;
    }

    private void triggerFraudCircuitBreaker() {
        log.error("=========================================================================");
        log.error("================ SHADOW BALANCE AUDIT CIRCUIT BREAKER ===================");
        log.error("TRAVANDO SAQUES AUTOMÁTICOS: O BANCO TEM MENOS FUNDOS DO QUE OS USUÁRIOS!");
        log.error("=========================================================================");

        // In real setup, this would set a Redis flag `system_halt_withdrawals=true`
        // disabling TransactionContract temporarily and alerting devs via Slack/PagerDuty.
        // For now, it logs the hyper-critical failure.
    }
}
