package source.treasury.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    private static final double TARGET_LIGHTNING_PCT = 0.70;
    private static final double TARGET_HOT_WALLET_PCT = 0.20;
    private static final double TARGET_COLD_STORAGE_PCT = 0.10;

    // Gatilho do Loop Out (Se Remote Balance cair abaixo de 20%, estouramos o limite de Inbound Liquidity)
    private static final double MIN_INBOUND_LIQUIDITY_PCT = 0.20;

    /**
     * Inspeciona se um canal Lightning precisa de um Loop Out (Submarine Swap).
     * @param localBalance saldo do nosso lado do canal
     * @param remoteBalance saldo da contraparte (nossa capacidade de RECEBER / Inbound)
     * @return true se precisar iniciar o Submarine Swap
     */
    public boolean requiresLoopOut(long localBalance, long remoteBalance) {
        long totalCapacity = localBalance + remoteBalance;
        if (totalCapacity == 0) return false;

        double remotePct = (double) remoteBalance / totalCapacity;

        if (remotePct < MIN_INBOUND_LIQUIDITY_PCT) {
            log.warn("[LiquidityBuffer] Inbound Liquidity Critica ({}% / Mínimo {}%). Loop Out Recomendado!",
                     String.format("%.2f", remotePct * 100),
                     String.format("%.2f", MIN_INBOUND_LIQUIDITY_PCT * 100));
            return true;
        }

        log.debug("[LiquidityBuffer] Canal Saudável: Inbound={}%, Outbound={}%",
                  String.format("%.2f", remotePct * 100),
                  String.format("%.2f", (1-remotePct) * 100));
        return false;
    }
}
