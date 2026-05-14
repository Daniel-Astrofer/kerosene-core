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

    /**
     * Acionado quando o agregador de liquidez detecta excesso de saldo em canais L2.
     * Retorna um estado seguro quando o provedor de swap ainda não foi validado.
     */
    public String executeLoopOut(BigDecimal amountToLoop) {
        log.warn("[LoopRebalance] automated loop out safety-locked; amountBtc={}", amountToLoop);
        log.warn("[LoopRebalance] rebalancing remains blocked until the swap provider passes automated policy checks.");

        return "SAFETY_LOCKED";
    }
}
