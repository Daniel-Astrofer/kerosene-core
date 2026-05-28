package source.treasury.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import source.treasury.domain.service.LiquidityRebalancePolicy;

/**
 * Gestor de Liquidez - Alocação e Rebalanceamento
 * Alvos Ideais:
 * - 70% Lightning (Canais: Operacional)
 * - 20% Hot Wallet (Saques On-chain)
 * - 10% Cold Storage (HODL / Reserva Seca)
 */
@Service
public class LiquidityBuffer {

    private static final Logger log = LoggerFactory.getLogger(LiquidityBuffer.class);
    private final LiquidityRebalancePolicy liquidityRebalancePolicy;

    public LiquidityBuffer(LiquidityRebalancePolicy liquidityRebalancePolicy) {
        this.liquidityRebalancePolicy = liquidityRebalancePolicy;
    }

    /**
     * Inspeciona se um canal Lightning precisa de um Loop Out (Submarine Swap).
     * @param localBalance saldo do nosso lado do canal
     * @param remoteBalance saldo da contraparte (nossa capacidade de RECEBER / Inbound)
     * @return true se precisar iniciar o Submarine Swap
     */
    public boolean requiresLoopOut(long localBalance, long remoteBalance) {
        double remotePct = liquidityRebalancePolicy.inboundLiquidityPercentage(localBalance, remoteBalance);

        if (liquidityRebalancePolicy.requiresLoopOut(localBalance, remoteBalance)) {
            log.warn("[LiquidityBuffer] Inbound Liquidity Critica ({}% / Mínimo {}%). Loop Out Recomendado!",
                     String.format("%.2f", remotePct * 100),
                     String.format("%.2f", liquidityRebalancePolicy.minimumInboundLiquidityPercentage() * 100));
            return true;
        }

        log.debug("[LiquidityBuffer] Canal Saudável: Inbound={}%, Outbound={}%",
                  String.format("%.2f", remotePct * 100),
                  String.format("%.2f", (1-remotePct) * 100));
        return false;
    }
}
