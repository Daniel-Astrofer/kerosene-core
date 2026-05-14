package source.treasury.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Oráculo de Taxas Dinâmicas
 * Prioriza o próprio nó (estimativas locais) com fallback.
 * Adiciona um markup de 10% + 500 sats para evitar que flutuações de mempool comam o lucro.
 */
@Service
public class FeeEstimatorService {

    private static final Logger log = LoggerFactory.getLogger(FeeEstimatorService.class);

    // Configurações de Markup (Insurance against fee spikes)
    private static final double NETWORK_MULTIPLICATOR = 1.10;
    private static final long FIXED_FEE_SATS = 500;

    /**
     * Calcula a taxa cobrada do usuário final com base na estimativa atual.
     * Retorna a taxa em Satoshis.
     */
    public long calculateUserFee(long estimatedNetworkFeeSats) {
        long finalFee = (long) Math.ceil(estimatedNetworkFeeSats * NETWORK_MULTIPLICATOR) + FIXED_FEE_SATS;
        log.info("[FeeEstimator] Rede: {} sats. Taxa final p/ Usuário (Markup 10% + 500sats): {} sats",
                 estimatedNetworkFeeSats, finalFee);
        return finalFee;
    }
}
