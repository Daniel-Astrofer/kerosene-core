package source.ledger.application.balance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import source.ledger.entity.LedgerEntity;
import source.ledger.exceptions.LedgerExceptions;
import source.ledger.repository.LedgerRepository;

@Service
public class LedgerIntegrityService {

    private static final Logger log = LoggerFactory.getLogger(LedgerIntegrityService.class);
    private static final String INTEGRITY_FAILURE_MESSAGE =
            "CRITICAL: Banco de Dados corrompido ou adulteração direta detectada no Saldo. Conta bloqueada imediatamente para segurança.";

    private final LedgerHashService ledgerHashService;
    private final LedgerRepository ledgerRepository;
    private final LedgerIntegrityFailurePort integrityFailurePort;

    public LedgerIntegrityService(
            LedgerHashService ledgerHashService,
            LedgerRepository ledgerRepository,
            LedgerIntegrityFailurePort integrityFailurePort) {
        this.ledgerHashService = ledgerHashService;
        this.ledgerRepository = ledgerRepository;
        this.integrityFailurePort = integrityFailurePort;
    }

    public void verifyBalanceIntegrity(LedgerEntity ledger) {
        if (ledger.getBalanceSignature() == null) {
            ledger.setBalanceSignature(ledgerHashService.generateBalanceSignature(ledger));
            ledgerRepository.save(ledger);
            return;
        }

        String expectedSignature = ledgerHashService.generateBalanceSignature(ledger);
        if (!expectedSignature.equals(ledger.getBalanceSignature())) {
            reportIntegrityFailure(ledger);

            throw new LedgerExceptions.LedgerIntegrityViolationException(INTEGRITY_FAILURE_MESSAGE);
        }
    }

    private void reportIntegrityFailure(LedgerEntity ledger) {
        try {
            integrityFailurePort.reportIntegrityFailure(new LedgerIntegrityFailure(
                    ledger.getId(),
                    walletId(ledger),
                    userId(ledger),
                    "BALANCE_SIGNATURE_MISMATCH"));
        } catch (Exception exception) {
            log.warn("[LedgerIntegrity] Integrity failure reporting failed for ledger {}: {}",
                    ledger.getId(),
                    exception.getMessage());
        }
    }

    private Long walletId(LedgerEntity ledger) {
        return ledger.getWallet() != null ? ledger.getWallet().getId() : null;
    }

    private Long userId(LedgerEntity ledger) {
        if (ledger.getWallet() == null || ledger.getWallet().getUser() == null) {
            return null;
        }
        return ledger.getWallet().getUser().getId();
    }
}
