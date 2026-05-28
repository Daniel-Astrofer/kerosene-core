package source.bitcoinaccounts.service;

import org.junit.jupiter.api.Test;
import source.bitcoinaccounts.model.BitcoinAccountEnums;
import source.bitcoinaccounts.model.TaxEventEntity;
import source.bitcoinaccounts.repository.TaxEventRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BitcoinTaxEventServiceTest {

    @Test
    void recordTemporaryEventIsIdempotentForSameOnchainReference() {
        TaxEventRepository repository = mock(TaxEventRepository.class);
        BitcoinTaxEventService service = new BitcoinTaxEventService(repository, 24);
        TaxEventEntity existing = new TaxEventEntity();
        existing.setUserId(42L);
        existing.setEventType(BitcoinAccountEnums.TaxEventType.DEPOSIT_INTERNAL);
        existing.setSourceTxid("tx123:0");

        when(repository.findFirstByUserIdAndEventTypeAndSourceTxid(
                42L,
                BitcoinAccountEnums.TaxEventType.DEPOSIT_INTERNAL,
                "tx123:0"))
                .thenReturn(Optional.of(existing));

        TaxEventEntity result = service.recordTemporaryEvent(
                42L,
                BitcoinAccountEnums.TaxEventType.DEPOSIT_INTERNAL,
                12_000L,
                "tx123:0",
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "USER_CLASSIFICATION_PENDING");

        assertEquals(existing, result);
        verify(repository, never()).save(any(TaxEventEntity.class));
    }

    @Test
    void classifyUsesAllowedSelfServiceLabels() {
        TaxEventRepository repository = mock(TaxEventRepository.class);
        BitcoinTaxEventService service = new BitcoinTaxEventService(repository, 24);
        TaxEventEntity event = new TaxEventEntity();
        event.setUserId(42L);
        event.setEventType(BitcoinAccountEnums.TaxEventType.DEPOSIT_INTERNAL);
        when(repository.findByIdAndUserId(event.getId(), 42L)).thenReturn(Optional.of(event));
        when(repository.save(any(TaxEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaxEventEntity result = service.classify(42L, event.getId(), "self_transfer");

        assertEquals("SELF_TRANSFER", result.getClassification());
        verify(repository).save(event);
    }

    @Test
    void csvExportUsesRedactedSourceReferences() {
        TaxEventRepository repository = mock(TaxEventRepository.class);
        BitcoinTaxEventService service = new BitcoinTaxEventService(repository, 24);
        TaxEventEntity event = new TaxEventEntity();
        event.setUserId(42L);
        event.setEventType(BitcoinAccountEnums.TaxEventType.COLD_WALLET_OBSERVED_IN);
        event.setQuantitySats(50_000L);
        event.setSourceTxid("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef:1");
        when(repository.findTop500ByUserIdAndPurgeAfterAfterOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of(event));

        String content = String.valueOf(service.export(42L, "csv").get("content"));

        assertTrue(content.contains("01234567...89abcdef:1"));
    }

    @Test
    void newTemporaryEventUsesConfiguredReadableRetention() {
        TaxEventRepository repository = mock(TaxEventRepository.class);
        BitcoinTaxEventService service = new BitcoinTaxEventService(repository, 6);
        when(repository.findFirstByUserIdAndEventTypeAndSourceTxid(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repository.save(any(TaxEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<TaxEventEntity> captor = ArgumentCaptor.forClass(TaxEventEntity.class);

        service.recordTemporaryEvent(
                42L,
                BitcoinAccountEnums.TaxEventType.DEPOSIT_INTERNAL,
                12_000L,
                "tx-retention:0",
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null);

        verify(repository).save(captor.capture());
        TaxEventEntity saved = captor.getValue();
        assertNotNull(saved.getPurgeAfter());
        assertTrue(saved.getPurgeAfter().isAfter(java.time.LocalDateTime.now().plusHours(5)));
        assertTrue(saved.getPurgeAfter().isBefore(java.time.LocalDateTime.now().plusHours(7)));
        assertTrue(saved.getMetadataRedacted().contains("\"ttlHours\":\"6\""));
    }
}
