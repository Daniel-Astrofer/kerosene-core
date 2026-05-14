package source.treasury.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import source.treasury.domain.service.FeeMarkupPolicy;

/**
 * Oráculo de Taxas Dinâmicas
 * Prioriza o próprio nó (estimativas locais) com fallback.
 * Adiciona um markup de 10% + 500 sats para evitar que flutuações de mempool comam o lucro.
 */
@Service
public class FeeEstimatorService {

    private static final Logger log = LoggerFactory.getLogger(FeeEstimatorService.class);
    private final FeeMarkupPolicy feeMarkupPolicy;

    public FeeEstimatorService(FeeMarkupPolicy feeMarkupPolicy) {
        this.feeMarkupPolicy = feeMarkupPolicy;
    }

    /**
     * Calcula a taxa cobrada do usuário final com base na estimativa atual.
     * Retorna a taxa em Satoshis.
     */
    public long calculateUserFee(long estimatedNetworkFeeSats) {
        long finalFee = feeMarkupPolicy.apply(estimatedNetworkFeeSats);
        log.info("[FeeEstimator] Rede: {} sats. Taxa final p/ Usuário (Markup 10% + 500sats): {} sats",
                 estimatedNetworkFeeSats, finalFee);
        return finalFee;
    }
}
