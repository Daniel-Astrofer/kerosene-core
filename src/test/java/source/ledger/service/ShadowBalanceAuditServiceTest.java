package source.ledger.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import source.ledger.application.balance.LedgerActiveUserPort;
import source.ledger.repository.LedgerRepository;
import source.security.VaultKeyProvider;
import source.treasury.service.ReserveBalanceService;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ShadowBalanceAuditServiceTest {

    @Test
    void scheduledAuditDoesNotLeakRepositoryFailureToScheduler() {
        LedgerRepository ledgerRepository = mock(LedgerRepository.class);
        ReserveBalanceService reserveBalanceService = mock(ReserveBalanceService.class);
        LedgerContract ledgerService = mock(LedgerContract.class);
        LedgerActiveUserPort activeUserPort = mock(LedgerActiveUserPort.class);
        VaultKeyProvider vaultKeyProvider = mock(VaultKeyProvider.class);
        when(vaultKeyProvider.isReady()).thenReturn(true);
        when(ledgerRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 500)))
                .thenThrow(new InvalidDataAccessResourceUsageException("schema unavailable"));

        ShadowBalanceAuditService service = new ShadowBalanceAuditService(
                ledgerRepository,
                reserveBalanceService,
                ledgerService,
                activeUserPort,
                vaultKeyProvider,
                true);

        assertDoesNotThrow(service::auditShadowBalance);
        verifyNoInteractions(reserveBalanceService, ledgerService, activeUserPort);
    }
}
