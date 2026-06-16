package source.payments.service;

import org.junit.jupiter.api.Test;
import source.ledger.service.LedgerContract;
import source.payments.model.PaymentEnums;
import source.payments.model.PaymentExecutionOutboxEntity;
import source.payments.model.PaymentIntentEntity;
import source.payments.repository.PaymentExecutionOutboxRepository;
import source.payments.repository.PaymentIntentRepository;
import source.wallet.repository.WalletRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentExternalReconciliationServiceTest {

    @Test
    void missingStatusClientMovesAcceptedIntentToReconciliationWithoutRefund() {
        PaymentIntentRepository intentRepository = mock(PaymentIntentRepository.class);
        PaymentExecutionOutboxRepository outboxRepository = mock(PaymentExecutionOutboxRepository.class);
        WalletRepository walletRepository = mock(WalletRepository.class);
        LedgerContract ledgerService = mock(LedgerContract.class);
        PaymentAuditService auditService = mock(PaymentAuditService.class);
        PaymentIntentEntity intent = intent(PaymentEnums.PaymentIntentStatus.ACCEPTED_BY_PROVIDER);
        PaymentExecutionOutboxEntity outbox = outbox(intent);
        when(intentRepository.findByIdForUpdate(intent.getId())).thenReturn(Optional.of(intent));
        when(outboxRepository.findByPaymentIntentId(intent.getId())).thenReturn(Optional.of(outbox));

        service(intentRepository, outboxRepository, walletRepository, ledgerService, auditService, List.of())
                .reconcile(intent.getId(), outbox);

        assertEquals(PaymentEnums.PaymentIntentStatus.REQUIRES_RECONCILIATION, intent.getStatus());
        assertEquals("PAYMENT_RECONCILIATION_PROVIDER_NOT_CONFIGURED", intent.getFailureCode());
        assertEquals("UNKNOWN", outbox.getStatus());
        verify(ledgerService, never()).updateBalance(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void settledProviderStatusSettlesIntentAndOutbox() {
        PaymentIntentRepository intentRepository = mock(PaymentIntentRepository.class);
        PaymentExecutionOutboxRepository outboxRepository = mock(PaymentExecutionOutboxRepository.class);
        WalletRepository walletRepository = mock(WalletRepository.class);
        LedgerContract ledgerService = mock(LedgerContract.class);
        PaymentAuditService auditService = mock(PaymentAuditService.class);
        PaymentIntentEntity intent = intent(PaymentEnums.PaymentIntentStatus.ACCEPTED_BY_PROVIDER);
        PaymentExecutionOutboxEntity outbox = outbox(intent);
        when(intentRepository.findByIdForUpdate(intent.getId())).thenReturn(Optional.of(intent));
        when(outboxRepository.findByPaymentIntentId(intent.getId())).thenReturn(Optional.of(outbox));

        service(
                intentRepository,
                outboxRepository,
                walletRepository,
                ledgerService,
                auditService,
                List.of(statusClient(PaymentRailStatusClient.StatusResult.settled("provider-ref", "SETTLED"))))
                .reconcile(intent.getId(), outbox);

        assertEquals(PaymentEnums.PaymentIntentStatus.SETTLED, intent.getStatus());
        assertEquals("SETTLED", outbox.getStatus());
        assertEquals("provider-ref", outbox.getProviderReference());
        verify(ledgerService, never()).updateBalance(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unknownProviderStatusKeepsIntentInReconciliation() {
        PaymentIntentRepository intentRepository = mock(PaymentIntentRepository.class);
        PaymentExecutionOutboxRepository outboxRepository = mock(PaymentExecutionOutboxRepository.class);
        WalletRepository walletRepository = mock(WalletRepository.class);
        LedgerContract ledgerService = mock(LedgerContract.class);
        PaymentAuditService auditService = mock(PaymentAuditService.class);
        PaymentIntentEntity intent = intent(PaymentEnums.PaymentIntentStatus.ACCEPTED_BY_PROVIDER);
        PaymentExecutionOutboxEntity outbox = outbox(intent);
        when(intentRepository.findByIdForUpdate(intent.getId())).thenReturn(Optional.of(intent));
        when(outboxRepository.findByPaymentIntentId(intent.getId())).thenReturn(Optional.of(outbox));

        service(
                intentRepository,
                outboxRepository,
                walletRepository,
                ledgerService,
                auditService,
                List.of(statusClient(PaymentRailStatusClient.StatusResult.unknown("provider-ref", "UNKNOWN"))))
                .reconcile(intent.getId(), outbox);

        assertEquals(PaymentEnums.PaymentIntentStatus.REQUIRES_RECONCILIATION, intent.getStatus());
        assertEquals("UNKNOWN", outbox.getStatus());
        assertEquals("provider-ref", outbox.getProviderReference());
        verify(ledgerService, never()).updateBalance(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void finalFailureRefundsAndFailsIntent() {
        PaymentIntentRepository intentRepository = mock(PaymentIntentRepository.class);
        PaymentExecutionOutboxRepository outboxRepository = mock(PaymentExecutionOutboxRepository.class);
        WalletRepository walletRepository = mock(WalletRepository.class);
        LedgerContract ledgerService = mock(LedgerContract.class);
        PaymentAuditService auditService = mock(PaymentAuditService.class);
        PaymentIntentEntity intent = intent(PaymentEnums.PaymentIntentStatus.REQUIRES_RECONCILIATION);
        PaymentExecutionOutboxEntity outbox = outbox(intent);
        when(intentRepository.findByIdForUpdate(intent.getId())).thenReturn(Optional.of(intent));
        when(outboxRepository.findByPaymentIntentId(intent.getId())).thenReturn(Optional.of(outbox));

        service(
                intentRepository,
                outboxRepository,
                walletRepository,
                ledgerService,
                auditService,
                List.of(statusClient(PaymentRailStatusClient.StatusResult.finalFailure(
                        "PROVIDER_FAILED_FINAL",
                        "Provider confirmed final failure."))))
                .reconcile(intent.getId(), outbox);

        assertEquals(PaymentEnums.PaymentIntentStatus.FAILED, intent.getStatus());
        assertEquals("PROVIDER_FAILED_FINAL", intent.getFailureCode());
        assertEquals("FAILED_FINAL", outbox.getStatus());
        verify(ledgerService).updateBalance(
                eq(10L),
                eq(new BigDecimal("0.00020240")),
                eq("PAYMENT_EXTERNAL_REFUND:" + intent.getId()));
    }

    private PaymentExternalReconciliationService service(
            PaymentIntentRepository intentRepository,
            PaymentExecutionOutboxRepository outboxRepository,
            WalletRepository walletRepository,
            LedgerContract ledgerService,
            PaymentAuditService auditService,
            List<PaymentRailStatusClient> clients) {
        return new PaymentExternalReconciliationService(
                intentRepository,
                outboxRepository,
                walletRepository,
                ledgerService,
                auditService,
                new PaymentStateMachine(),
                clients);
    }

    private PaymentRailStatusClient statusClient(PaymentRailStatusClient.StatusResult result) {
        return new PaymentRailStatusClient() {
            @Override
            public PaymentEnums.PaymentRail rail() {
                return PaymentEnums.PaymentRail.LIGHTNING;
            }

            @Override
            public StatusResult status(PaymentIntentEntity intent, PaymentExecutionOutboxEntity outbox) {
                return result;
            }
        };
    }

    private PaymentIntentEntity intent(PaymentEnums.PaymentIntentStatus status) {
        PaymentIntentEntity intent = new PaymentIntentEntity();
        org.springframework.test.util.ReflectionTestUtils.setField(intent, "id", UUID.randomUUID());
        intent.setSenderUserId(1L);
        intent.setLockedWalletId(10L);
        intent.setRail(PaymentEnums.PaymentRail.LIGHTNING);
        intent.setFeeMode(PaymentEnums.FeeMode.SENDER_PAYS);
        intent.setRequestedAmountFiat(new BigDecimal("100.00"));
        intent.setRequestedAmountSats(20_000L);
        intent.setReceiverAmountSats(20_000L);
        intent.setTotalDebitSats(20_240L);
        intent.setNetworkFeeSats(60L);
        intent.setKeroseneFeeSats(180L);
        intent.setFxRate(new BigDecimal("500000.00"));
        intent.setQuoteExpiresAt(Instant.now().plusSeconds(120));
        intent.setStatus(status);
        return intent;
    }

    private PaymentExecutionOutboxEntity outbox(PaymentIntentEntity intent) {
        PaymentExecutionOutboxEntity outbox = new PaymentExecutionOutboxEntity();
        outbox.setPaymentIntentId(intent.getId());
        outbox.setRail(intent.getRail().name());
        outbox.setIdempotencyKey("idem-recon");
        outbox.setStatus("DISPATCHED");
        outbox.setProviderReference("existing-ref");
        outbox.setNextAttemptAt(Instant.now().minusSeconds(60));
        return outbox;
    }
}
