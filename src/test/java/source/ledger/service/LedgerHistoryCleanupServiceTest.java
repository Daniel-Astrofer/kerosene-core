package source.ledger.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import source.ledger.repository.LedgerTransactionHistoryRepository;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LedgerHistoryCleanupServiceTest {

    @Test
    void deletesReadableHistoryOlderThanConfiguredHours() {
        LedgerTransactionHistoryRepository repository = mock(LedgerTransactionHistoryRepository.class);
        when(repository.deleteByCreatedAtBefore(org.mockito.ArgumentMatchers.any())).thenReturn(3);
        LedgerHistoryCleanupService service = new LedgerHistoryCleanupService(repository, 24);

        LocalDateTime before = LocalDateTime.now().minusHours(24).minusMinutes(1);
        service.cleanupOldHistory();
        LocalDateTime after = LocalDateTime.now().minusHours(24).plusMinutes(1);

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).deleteByCreatedAtBefore(cutoff.capture());
        assertTrue(cutoff.getValue().isAfter(before));
        assertTrue(cutoff.getValue().isBefore(after));
    }
}
