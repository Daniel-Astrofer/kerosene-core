package source.transactions.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.common.observability.FinancialOperationsMetrics;
import source.transactions.model.NetworkTransferEventEntity;
import source.transactions.repository.NetworkTransferEventRepository;
import source.treasury.service.FinancialAuditTrailService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NetworkTransferEventServiceTest {

    @Mock
    private NetworkTransferEventRepository eventRepository;

    @Mock
    private FinancialAuditTrailService auditTrailService;

    @Mock
    private FinancialOperationsMetrics metrics;

    @Test
    void redactsSensitiveProviderPayloadBeforePersistence() {
        when(eventRepository.save(any(NetworkTransferEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        NetworkTransferEventService service =
                new NetworkTransferEventService(eventRepository, auditTrailService, metrics);

        String invoice = "lnbc1p" + "a".repeat(80);
        String address = "tb1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh";
        String payload = "{\"invoice\":\"" + invoice + "\","
                + "\"seed\":\"abandon abandon abandon\","
                + "\"address\":\"" + address + "\","
                + "\"authorization\":\"Bearer abcdefghijklmnopqrstuvwxyz123456\"}";

        service.warn(42L, "BTCPAY_WEBHOOK_ORPHAN", "invoice-1", payload);

        ArgumentCaptor<NetworkTransferEventEntity> eventCaptor =
                ArgumentCaptor.forClass(NetworkTransferEventEntity.class);
        verify(eventRepository).save(eventCaptor.capture());
        String persisted = eventCaptor.getValue().getPayload();

        assertFalse(persisted.contains(invoice));
        assertFalse(persisted.contains(address));
        assertFalse(persisted.contains("abandon abandon"));
        assertFalse(persisted.contains("abcdefghijklmnopqrstuvwxyz123456"));
        assertTrue(persisted.contains("***") || persisted.contains("..."));
    }
}
