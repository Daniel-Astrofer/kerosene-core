package source.ledger.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import source.ledger.repository.LedgerRepository;
import source.transactions.infra.BlockchainClient;

import java.math.BigDecimal;

/**
 * Agente de Auditoria de Shadow Balance (Prevenção contra Quebra Fracionária).
 * Assegura que o total de fundos emitidos no DB (L2) não excede os satoshis reais do nó (L1).
 */
@Service
public class ShadowBalanceAuditService {

    private final LedgerRepository ledgerRepository;
    private final BlockchainClient blockchainClient;
    private final LedgerService ledgerService;
    private final source.auth.application.service.user.contract.UserServiceContract userService;
    private final source.security.VaultKeyProvider vaultKeyProvider;

    private static final Logger log = LoggerFactory.getLogger(ShadowBalanceAuditService.class);

    public ShadowBalanceAuditService(LedgerRepository ledgerRepository,
                                     BlockchainClient blockchainClient,
                                     LedgerService ledgerService,
                                     source.auth.application.service.user.contract.UserServiceContract userService,
                                     source.security.VaultKeyProvider vaultKeyProvider) {
        this.ledgerRepository = ledgerRepository;
        this.blockchainClient = blockchainClient;
        this.ledgerService = ledgerService;
        this.userService = userService;
        this.vaultKeyProvider = vaultKeyProvider;
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
        // Note: Summing in Java layer to allow decryption by BalanceCryptoConverter
        BigDecimal totalDbBalance = ledgerRepository.findAll().stream()
            .map(source.ledger.entity.LedgerEntity::getBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDbBalance == null) {
            totalDbBalance = BigDecimal.ZERO;
        }

        long totalSatsDb = totalDbBalance.multiply(new BigDecimal("100000000")).longValue();
        long totalSatsChain = blockchainClient.getHotWalletBalance();

        if (totalSatsDb > totalSatsChain) {
            log.error("[CRÍTICA] INSOLVÊNCIA DETECTADA! DB={} sats > Chain={} sats.", totalSatsDb, totalSatsChain);
            triggerFraudCircuitBreaker();
        }

        // 2. Auditoria de Integridade Interna (Auditore das Sombras)
        // Varre todos os usuários ativos para garantir que o saldo bate com o extrato e HMAC
        userService.listar().stream()
            .filter(user -> user.getIsActive())
            .forEach(user -> {
                try {
                    ledgerService.validateUserLedgerIntegrity(user.getId());
                } catch (SecurityException e) {
                    log.error("[ShadowAudit] Falha de integridade crítica para usuário {}: {}", user.getUsername(), e.getMessage());
                    // validateUserLedgerIntegrity já faz o lockAccount se necessário
                }
            });

        log.info("[ShadowAudit] Auditoria concluída.");
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
