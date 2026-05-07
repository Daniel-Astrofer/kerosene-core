package source.transactions.service;

import org.junit.jupiter.api.Test;
import source.common.observability.FinancialOperationsMetrics;
import source.transactions.model.ExternalProviderOutboxEntity;
import source.transactions.repository.ExternalProviderOutboxRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalProviderOutboxServiceTest {

    @Test
    void enqueuePersistsDurableProviderIntent() {
        ExternalProviderOutboxRepository repository = mock(ExternalProviderOutboxRepository.class);
        NetworkTransferEventService eventService = mock(NetworkTransferEventService.class);
        ExternalProviderOutboxService service = new ExternalProviderOutboxService(repository, eventService);
        when(repository.saveAndFlush(any(ExternalProviderOutboxEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UUID transferId = UUID.randomUUID();
        ExternalProviderOutboxEntity outbox = service.enqueue(
                transferId,
                "ONCHAIN_SEND",
                "idem-ref",
                "{\"amountSats\":1}");

        assertEquals(transferId, outbox.getTransferId());
        assertEquals("ONCHAIN_SEND", outbox.getOperationType());
        assertEquals("PENDING", outbox.getStatus());
        verify(eventService).info((Long) null, "PROVIDER_OUTBOX_ENQUEUED", source.common.infra.logging.LogSanitizer.fingerprint("idem-ref"),
                "transferId=" + transferId + " | operationType=ONCHAIN_SEND");
    }

    @Test
    void markFailedMovesOutboxToFinalFailure() {
        ExternalProviderOutboxRepository repository = mock(ExternalProviderOutboxRepository.class);
        ExternalProviderOutboxService service = new ExternalProviderOutboxService(
                repository,
                mock(NetworkTransferEventService.class));
        ExternalProviderOutboxEntity entity = new ExternalProviderOutboxEntity();
        UUID id = entity.getId();
        entity.setStatus("PROCESSING");
        entity.setClaimedBy("worker-a");
        entity.setClaimedAt(LocalDateTime.now());
        when(repository.findById(id)).thenReturn(Optional.of(entity));

        service.markFailed(id, "provider down", false);

        assertEquals("FAILED_FINAL", entity.getStatus());
        assertEquals(1, entity.getAttempts());
        assertEquals(null, entity.getClaimedBy());
        verify(repository).save(entity);
    }

    @Test
    void markUnknownStopsAutomaticRetryAndKeepsProviderReference() {
        ExternalProviderOutboxRepository repository = mock(ExternalProviderOutboxRepository.class);
        ExternalProviderOutboxService service = new ExternalProviderOutboxService(
                repository,
                mock(NetworkTransferEventService.class));
        ExternalProviderOutboxEntity entity = new ExternalProviderOutboxEntity();
        UUID id = entity.getId();
        entity.setStatus("PROCESSING");
        entity.setClaimedBy("worker-a");
        entity.setClaimedAt(LocalDateTime.now());
        when(repository.findById(id)).thenReturn(Optional.of(entity));

        service.markUnknown(id, "psbt-hash", "broadcast timeout");

        assertEquals("UNKNOWN", entity.getStatus());
        assertEquals("psbt-hash", entity.getProviderReference());
        assertEquals(1, entity.getAttempts());
        assertEquals(null, entity.getClaimedBy());
        verify(repository).save(entity);
    }

    @Test
    void claimDueClaimsOnlyRowsWonByThisWorker() {
        ExternalProviderOutboxRepository repository = mock(ExternalProviderOutboxRepository.class);
        ExternalProviderOutboxService service = new ExternalProviderOutboxService(
                repository,
                mock(NetworkTransferEventService.class));
        ExternalProviderOutboxEntity claimed = new ExternalProviderOutboxEntity();
        claimed.setStatus("PENDING");
        ExternalProviderOutboxEntity lostRace = new ExternalProviderOutboxEntity();
        lostRace.setStatus("FAILED_RETRYABLE");

        when(repository.findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                anyCollection(),
                any(LocalDateTime.class))).thenReturn(List.of(claimed, lostRace));
        when(repository.claimDue(eq(claimed.getId()), anyCollection(), any(LocalDateTime.class), any(LocalDateTime.class), eq("worker-a")))
                .thenReturn(1);
        when(repository.claimDue(eq(lostRace.getId()), anyCollection(), any(LocalDateTime.class), any(LocalDateTime.class), eq("worker-a")))
                .thenReturn(0);
        when(repository.findById(claimed.getId())).thenReturn(Optional.of(claimed));

        List<ExternalProviderOutboxEntity> result = service.claimDue("Worker-A");

        assertEquals(List.of(claimed), result);
    }

    @Test
    void rejectsMissingIdempotencyKey() {
        ExternalProviderOutboxService service = new ExternalProviderOutboxService(
                mock(ExternalProviderOutboxRepository.class),
                mock(NetworkTransferEventService.class));

        assertThrows(IllegalArgumentException.class,
                () -> service.enqueue(UUID.randomUUID(), "ONCHAIN_SEND", " ", "{}"));
    }
}
