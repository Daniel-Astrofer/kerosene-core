package source.treasury.infra.audit;

import org.springframework.stereotype.Component;
import source.ledger.repository.LedgerRepository;
import source.treasury.application.port.out.LedgerLiabilityPort;

import java.math.BigDecimal;

@Component
public class LedgerLiabilityAdapter implements LedgerLiabilityPort {

    private final LedgerRepository ledgerRepository;

    public LedgerLiabilityAdapter(LedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    @Override
    public BigDecimal loadTotalLiabilities() {
        BigDecimal total = ledgerRepository.sumAllBalances();
        return total != null ? total : BigDecimal.ZERO;
    }
}
