package source.payments.service;

import org.junit.jupiter.api.Test;
import source.payments.model.PaymentExecutionOutboxEntity;
import source.payments.repository.PaymentExecutionOutboxRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentExecutionOutboxServiceTest {

    @Test
    void claimDueReturnsOnlyAtomicallyClaimedRows() {
        PaymentExecutionOutboxRepository repository = mock(PaymentExecutionOutboxRepository.class);
        PaymentExecutionOutboxService service = new PaymentExecutionOutboxService(repository);
        PaymentExecutionOutboxEntity claimed = outbox("PENDING");
        PaymentExecutionOutboxEntity skipped = outbox("FAILED_RETRYABLE");
        claimed.setStatus("PROCESSING");
        claimed.setClaimedBy("worker-a");
        claimed.setClaimedAt(Instant.now());

        when(repository.findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(anyList(), any()))
                .thenReturn(List.of(claimed, skipped));
        when(repository.claimDue(eq(claimed.getId()), anyList(), any(), any(), eq("worker-a"))).thenReturn(1);
        when(repository.claimDue(eq(skipped.getId()), anyList(), any(), any(), eq("worker-a"))).thenReturn(0);
        when(repository.findById(claimed.getId())).thenReturn(Optional.of(claimed));

        List<PaymentExecutionOutboxEntity> result = service.claimDue("Worker-A");

        assertEquals(List.of(claimed), result);
        verify(repository).claimDue(eq(claimed.getId()), eq(List.of("PENDING", "FAILED_RETRYABLE")), any(), any(), eq("worker-a"));
        verify(repository).claimDue(eq(skipped.getId()), eq(List.of("PENDING", "FAILED_RETRYABLE")), any(), any(), eq("worker-a"));
    }

    private PaymentExecutionOutboxEntity outbox(String status) {
        PaymentExecutionOutboxEntity outbox = new PaymentExecutionOutboxEntity();
        outbox.setPaymentIntentId(java.util.UUID.randomUUID());
        outbox.setRail("LIGHTNING");
        outbox.setIdempotencyKey(java.util.UUID.randomUUID().toString());
        outbox.setStatus(status);
        outbox.setNextAttemptAt(Instant.now().minusSeconds(1));
        return outbox;
    }
}
