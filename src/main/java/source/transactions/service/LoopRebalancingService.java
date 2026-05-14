package source.transactions.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Agente de Rebalanceamento Automático (Loop Out)
 * Converte fundos mantidos em L2 (Canais Lightning) de volta para L1 (Cold/Hot Wallet On-chain)
 * utilizando provedores de Submarine Swaps como Boltz ou Lightning Labs Loop.
 */
@Service
public class LoopRebalancingService {

    private static final Logger log = LoggerFactory.getLogger(LoopRebalancingService.class);

    // Removed threshold logic as it's now in manual mode

    /**
     * Acionado quando o agregador de liquidez detecta excesso de saldo em canais L2.
     * Retorna o ID do swap gerado pelo provedor.
     */
    public String executeLoopOut(BigDecimal amountToLoop) {
        // Agente 6: Safety Lock - Draft Mode enforced. Automated swaps are disabled
        // to prevent Lightning channel lockups if the Submarine Swap API encounters errors.
        log.warn("[LoopRebalance] 🚨 AUTOMATED SWAP DISABLED (DRAFT MODE) 🚨");
        log.warn("=========================================================================");
        log.warn("[TELEGRAM ALERT] PING @admin - High L2 inbound capacity detected. ");
        log.warn("Manual Loop Out required for {} BTC.", amountToLoop);
        log.warn("Rebalancing suspended until Boltz API is fully validated in Testnet.");
        log.warn("=========================================================================");

        // Retorna status indicando que requer intervenção manual.
        return "MANUAL_REVIEW_REQUIRED";
    }
}
