package source.treasury.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import source.treasury.entity.FinancialAuditEventEntity;
import source.treasury.repository.FinancialAuditEventRepository;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinancialAuditTrailServiceTest {

    @Test
    void createsHashChainedAuditEvents() {
        FinancialAuditEventRepository repository = mock(FinancialAuditEventRepository.class);
        FinancialAuditTrailService service = new FinancialAuditTrailService(repository, new ObjectMapper());
        when(repository.findTopByOrderBySequenceNumberDesc()).thenReturn(Optional.empty());
        when(repository.save(any(FinancialAuditEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FinancialAuditEventEntity first = service.record(
                "LEDGER_BALANCE_MUTATION",
                "LEDGER",
                "10",
                1L,
                "ctx",
                Map.of("amount", "0.00000001"));

        assertEquals("0".repeat(64), first.getPreviousHash());
        assertNotNull(first.getPayloadHash());
        assertNotNull(first.getEventHash());

        when(repository.findTopByOrderBySequenceNumberDesc()).thenReturn(Optional.of(first));
        FinancialAuditEventEntity second = service.record(
                "NETWORK_TRANSFER_EVENT",
                "NETWORK_TRANSFER",
                "20",
                1L,
                "tx",
                Map.of("status", "CONFIRMED"));

        assertEquals(first.getEventHash(), second.getPreviousHash());
        assertNotEquals(first.getEventHash(), second.getEventHash());
    }
}
