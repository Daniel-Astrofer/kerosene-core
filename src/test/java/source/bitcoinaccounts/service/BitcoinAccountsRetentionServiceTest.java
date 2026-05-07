package source.bitcoinaccounts.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BitcoinAccountsRetentionServiceTest {

    @Test
    void retentionUsesAbsolutePurgeAfterTimestamp() {
        ReceivingRequestService receivingRequestService = mock(ReceivingRequestService.class);
        BitcoinTaxEventService taxEventService = mock(BitcoinTaxEventService.class);
        BitcoinAccountsRetentionService service = new BitcoinAccountsRetentionService(
                receivingRequestService,
                taxEventService,
                24);
        ArgumentCaptor<LocalDateTime> receiveCutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> taxCutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        service.enforceRetention();

        LocalDateTime after = LocalDateTime.now().plusSeconds(1);
        verify(receivingRequestService).expireDueRequests();
        verify(receivingRequestService).purgeReadableReceiveData(receiveCutoff.capture());
        verify(taxEventService).purgeReadableEventsOlderThan(taxCutoff.capture());
        assertTrue(!receiveCutoff.getValue().isBefore(before) && !receiveCutoff.getValue().isAfter(after));
        assertTrue(!taxCutoff.getValue().isBefore(before) && !taxCutoff.getValue().isAfter(after));
    }
}
