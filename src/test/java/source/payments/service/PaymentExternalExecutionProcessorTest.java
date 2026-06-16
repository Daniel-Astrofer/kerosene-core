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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentExternalExecutionProcessorTest {

    @Test
    void outboxNotFoundDoesNothing() {
        PaymentExecutionOutboxRepository outboxRepository = mock(PaymentExecutionOutboxRepository.class);
        when(outboxRepository.findByIdForUpdate(org.mockito.ArgumentMatchers.any(UUID.class))).thenReturn(Optional.empty());

        PaymentExternalExecutionProcessor processor = new PaymentExternalExecutionProcessor(
                outboxRepository, mock(PaymentIntentRepository.class), mock(WalletRepository.class),
                mock(LedgerContract.class), mock(PaymentAuditService.class), new PaymentStateMachine(), List.of());

        processor.process(UUID.randomUUID());

        verifyNoInteractions(mock(PaymentIntentRepository.class));
    }

    @Test
    void outboxNotClaimedDoesNothing() {
        PaymentExecutionOutboxRepository outboxRepository = mock(PaymentExecutionOutboxRepository.class);
        PaymentExecutionOutboxEntity outbox = outbox(processingIntent());
        outbox.setStatus("PENDING");
        outbox.setClaimedBy(null);
        when(outboxRepository.findByIdForUpdate(org.mockito.ArgumentMatchers.any(UUID.class))).thenReturn(Optional.of(outbox));

        PaymentExternalExecutionProcessor processor = new PaymentExternalExecutionProcessor(
                outboxRepository, mock(PaymentIntentRepository.class), mock(WalletRepository.class),
                mock(LedgerContract.class), mock(PaymentAuditService.class), new PaymentStateMachine(), List.of());

        processor.process(UUID.randomUUID());

        verifyNoInteractions(mock(PaymentIntentRepository.class));
    }

    @Test
    void intentNotFoundMarksOutboxFinal() {
        PaymentExecutionOutboxRepository outboxRepository = mock(PaymentExecutionOutboxRepository.class);
        PaymentIntentRepository intentRepository = mock(PaymentIntentRepository.class);
        PaymentExecutionOutboxEntity outbox = outbox(processingIntent());
        when(outboxRepository.findByIdForUpdate(outbox.getId())).thenReturn(Optional.of(outbox));
        when(intentRepository.findByIdForUpdate(outbox.getPaymentIntentId())).thenReturn(Optional.empty());

        PaymentExternalExecutionProcessor processor = new PaymentExternalExecutionProcessor(
                outboxRepository, intentRepository, mock(WalletRepository.class),
                mock(LedgerContract.class), mock(PaymentAuditService.class), new PaymentStateMachine(), List.of());

        processor.process(outbox.getId());

        assertEquals("FAILED_FINAL", outbox.getStatus());
        assertEquals("PAYMENT_INTENT_NOT_PROCESSING: O envio nao esta mais em processamento.", outbox.getLastError());
    }

    @Test
    void intentNotProcessingMarksOutboxFinal() {
        PaymentExecutionOutboxRepository outboxRepository = mock(PaymentExecutionOutboxRepository.class);
        PaymentIntentRepository intentRepository = mock(PaymentIntentRepository.class);
        PaymentIntentEntity intent = processingIntent();
        intent.setStatus(PaymentEnums.PaymentIntentStatus.FAILED);
        PaymentExecutionOutboxEntity outbox = outbox(intent);
        when(outboxRepository.findByIdForUpdate(outbox.getId())).thenReturn(Optional.of(outbox));
        when(intentRepository.findByIdForUpdate(intent.getId())).thenReturn(Optional.of(intent));

        PaymentExternalExecutionProcessor processor = new PaymentExternalExecutionProcessor(
                outboxRepository, intentRepository, mock(WalletRepository.class),
                mock(LedgerContract.class), mock(PaymentAuditService.class), new PaymentStateMachine(), List.of());

        processor.process(outbox.getId());

        assertEquals("FAILED_FINAL", outbox.getStatus());
        assertEquals("PAYMENT_INTENT_NOT_PROCESSING: O envio nao esta mais em processamento.", outbox.getLastError());
    }

    @Test
    void missingExecutorRefundsLockedBalanceAndFailsIntent() {
        PaymentExecutionOutboxRepository outboxRepository = mock(PaymentExecutionOutboxRepository.class);
        PaymentIntentRepository intentRepository = mock(PaymentIntentRepository.class);
        WalletRepository walletRepository = mock(WalletRepository.class);
        LedgerContract ledgerService = mock(LedgerContract.class);
        PaymentAuditService auditService = mock(PaymentAuditService.class);
        PaymentIntentEntity intent = processingIntent();
        PaymentExecutionOutboxEntity outbox = outbox(intent);
        when(outboxRepository.findByIdForUpdate(outbox.getId())).thenReturn(Optional.of(outbox));
        when(intentRepository.findByIdForUpdate(intent.getId())).thenReturn(Optional.of(intent));

        PaymentExternalExecutionProcessor processor = new PaymentExternalExecutionProcessor(
                outboxRepository,
                intentRepository,
                walletRepository,
                ledgerService,
                auditService,
                new PaymentStateMachine(),
                List.of());

        processor.process(outbox.getId());

        assertEquals(PaymentEnums.PaymentIntentStatus.FAILED, intent.getStatus());
        assertEquals("PAYMENT_RAIL_EXECUTION_NOT_CONFIGURED", intent.getFailureCode());
        assertEquals("FAILED_FINAL", outbox.getStatus());
        assertNull(outbox.getClaimedBy());
        verify(ledgerService).updateBalance(eq(10L), eq(new BigDecimal("0.00020240")), eq("PAYMENT_EXTERNAL_REFUND:" + intent.getId()));
    }

    @Test
    void configuredExecutorMarksOutboxDispatchedAndIntentAcceptedWithoutRefund() {
        PaymentExecutionOutboxRepository outboxRepository = mock(PaymentExecutionOutboxRepository.class);
        PaymentIntentRepository intentRepository = mock(PaymentIntentRepository.class);
        WalletRepository walletRepository = mock(WalletRepository.class);
        LedgerContract ledgerService = mock(LedgerContract.class);
        PaymentAuditService auditService = mock(PaymentAuditService.class);
        PaymentIntentEntity intent = processingIntent();
        PaymentExecutionOutboxEntity outbox = outbox(intent);
        when(outboxRepository.findByIdForUpdate(outbox.getId())).thenReturn(Optional.of(outbox));
        when(intentRepository.findByIdForUpdate(intent.getId())).thenReturn(Optional.of(intent));

        PaymentExternalExecutionProcessor processor = new PaymentExternalExecutionProcessor(
                outboxRepository,
                intentRepository,
                walletRepository,
                ledgerService,
                auditService,
                new PaymentStateMachine(),
                List.of(new PaymentRailExecutor() {
                    @Override
                    public PaymentEnums.PaymentRail rail() {
                        return PaymentEnums.PaymentRail.LIGHTNING;
                    }

                    @Override
                    public ExecutionResult execute(PaymentIntentEntity intent) {
                        return new ExecutionResult("payment-ref-1");
                    }
                }));

        processor.process(outbox.getId());

        assertEquals(PaymentEnums.PaymentIntentStatus.ACCEPTED_BY_PROVIDER, intent.getStatus());
        assertEquals("DISPATCHED", outbox.getStatus());
        assertEquals("payment-ref-1", outbox.getProviderReference());
        assertNull(outbox.getClaimedBy());
        verify(ledgerService, never()).updateBalance(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void settledExecutorMarksIntentSettled() {
        PaymentExecutionOutboxRepository outboxRepository = mock(PaymentExecutionOutboxRepository.class);
        PaymentIntentRepository intentRepository = mock(PaymentIntentRepository.class);
        WalletRepository walletRepository = mock(WalletRepository.class);
        LedgerContract ledgerService = mock(LedgerContract.class);
        PaymentAuditService auditService = mock(PaymentAuditService.class);
        PaymentIntentEntity intent = processingIntent();
        PaymentExecutionOutboxEntity outbox = outbox(intent);
        when(outboxRepository.findByIdForUpdate(outbox.getId())).thenReturn(Optional.of(outbox));
        when(intentRepository.findByIdForUpdate(intent.getId())).thenReturn(Optional.of(intent));

        PaymentExternalExecutionProcessor processor = new PaymentExternalExecutionProcessor(
                outboxRepository,
                intentRepository,
                walletRepository,
                ledgerService,
                auditService,
                new PaymentStateMachine(),
                List.of(executorReturning(PaymentRailExecutor.ExecutionResult.settled("payment-ref-2", "SETTLED"))));

        processor.process(outbox.getId());

        assertEquals(PaymentEnums.PaymentIntentStatus.SETTLED, intent.getStatus());
        assertEquals("SETTLED", outbox.getStatus());
        assertEquals("payment-ref-2", outbox.getProviderReference());
        assertNull(outbox.getClaimedBy());
        verify(ledgerService, never()).updateBalance(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unknownOutcomeMovesIntentToReconciliationWithoutRefund() {
        PaymentExecutionOutboxRepository outboxRepository = mock(PaymentExecutionOutboxRepository.class);
        PaymentIntentRepository intentRepository = mock(PaymentIntentRepository.class);
        WalletRepository walletRepository = mock(WalletRepository.class);
        LedgerContract ledgerService = mock(LedgerContract.class);
        PaymentAuditService auditService = mock(PaymentAuditService.class);
        PaymentIntentEntity intent = processingIntent();
        PaymentExecutionOutboxEntity outbox = outbox(intent);
        when(outboxRepository.findByIdForUpdate(outbox.getId())).thenReturn(Optional.of(outbox));
        when(intentRepository.findByIdForUpdate(intent.getId())).thenReturn(Optional.of(intent));

        PaymentExternalExecutionProcessor processor = new PaymentExternalExecutionProcessor(
                outboxRepository,
                intentRepository,
                walletRepository,
                ledgerService,
                auditService,
                new PaymentStateMachine(),
                List.of(executorReturning(PaymentRailExecutor.ExecutionResult.unknown("payment-ref-3", "TIMEOUT"))));

        processor.process(outbox.getId());

        assertEquals(PaymentEnums.PaymentIntentStatus.REQUIRES_RECONCILIATION, intent.getStatus());
        assertEquals("PAYMENT_EXTERNAL_EXECUTION_UNKNOWN", intent.getFailureCode());
        assertEquals("UNKNOWN", outbox.getStatus());
        assertEquals("payment-ref-3", outbox.getProviderReference());
        assertNull(outbox.getClaimedBy());
        verify(ledgerService, never()).updateBalance(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void finalFailureOutcomeRefundsLockedBalanceAndFailsIntent() {
        PaymentExecutionOutboxRepository outboxRepository = mock(PaymentExecutionOutboxRepository.class);
        PaymentIntentRepository intentRepository = mock(PaymentIntentRepository.class);
        WalletRepository walletRepository = mock(WalletRepository.class);
        LedgerContract ledgerService = mock(LedgerContract.class);
        PaymentAuditService auditService = mock(PaymentAuditService.class);
        PaymentIntentEntity intent = processingIntent();
        PaymentExecutionOutboxEntity outbox = outbox(intent);
        when(outboxRepository.findByIdForUpdate(outbox.getId())).thenReturn(Optional.of(outbox));
        when(intentRepository.findByIdForUpdate(intent.getId())).thenReturn(Optional.of(intent));

        PaymentExternalExecutionProcessor processor = new PaymentExternalExecutionProcessor(
                outboxRepository,
                intentRepository,
                walletRepository,
                ledgerService,
                auditService,
                new PaymentStateMachine(),
                List.of(executorReturning(PaymentRailExecutor.ExecutionResult.finalFailure(
                        "PAYMENT_PROVIDER_REJECTED",
                        "Provider recusou o envio antes de aceitar a transferencia."))));

        processor.process(outbox.getId());

        assertEquals(PaymentEnums.PaymentIntentStatus.FAILED, intent.getStatus());
        assertEquals("PAYMENT_PROVIDER_REJECTED", intent.getFailureCode());
        assertEquals("FAILED_FINAL", outbox.getStatus());
        assertEquals("PAYMENT_PROVIDER_REJECTED: Provider recusou o envio antes de aceitar a transferencia.", outbox.getLastError());
        assertNull(outbox.getClaimedBy());
        verify(ledgerService).updateBalance(eq(10L), eq(new BigDecimal("0.00020240")), eq("PAYMENT_EXTERNAL_REFUND:" + intent.getId()));
    }

    @Test
    void executorExceptionMovesIntentToReconciliationWithoutRefund() {
        PaymentExecutionOutboxRepository outboxRepository = mock(PaymentExecutionOutboxRepository.class);
        PaymentIntentRepository intentRepository = mock(PaymentIntentRepository.class);
        WalletRepository walletRepository = mock(WalletRepository.class);
        LedgerContract ledgerService = mock(LedgerContract.class);
        PaymentAuditService auditService = mock(PaymentAuditService.class);
        PaymentIntentEntity intent = processingIntent();
        PaymentExecutionOutboxEntity outbox = outbox(intent);
        when(outboxRepository.findByIdForUpdate(outbox.getId())).thenReturn(Optional.of(outbox));
        when(intentRepository.findByIdForUpdate(intent.getId())).thenReturn(Optional.of(intent));

        PaymentExternalExecutionProcessor processor = new PaymentExternalExecutionProcessor(
                outboxRepository,
                intentRepository,
                walletRepository,
                ledgerService,
                auditService,
                new PaymentStateMachine(),
                List.of(new PaymentRailExecutor() {
                    @Override
                    public PaymentEnums.PaymentRail rail() {
                        return PaymentEnums.PaymentRail.LIGHTNING;
                    }

                    @Override
                    public ExecutionResult execute(PaymentIntentEntity intent) {
                        throw new IllegalStateException("provider timeout");
                    }
                }));

        processor.process(outbox.getId());

        assertEquals(PaymentEnums.PaymentIntentStatus.REQUIRES_RECONCILIATION, intent.getStatus());
        assertEquals("PAYMENT_EXTERNAL_EXECUTION_UNKNOWN", intent.getFailureCode());
        assertEquals("UNKNOWN", outbox.getStatus());
        assertNull(outbox.getClaimedBy());
        verify(ledgerService, never()).updateBalance(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void retryableOutcomeKeepsIntentProcessingAndSchedulesSafeRetry() {
        PaymentExecutionOutboxRepository outboxRepository = mock(PaymentExecutionOutboxRepository.class);
        PaymentIntentRepository intentRepository = mock(PaymentIntentRepository.class);
        WalletRepository walletRepository = mock(WalletRepository.class);
        LedgerContract ledgerService = mock(LedgerContract.class);
        PaymentAuditService auditService = mock(PaymentAuditService.class);
        PaymentIntentEntity intent = processingIntent();
        PaymentExecutionOutboxEntity outbox = outbox(intent);
        when(outboxRepository.findByIdForUpdate(outbox.getId())).thenReturn(Optional.of(outbox));
        when(intentRepository.findByIdForUpdate(intent.getId())).thenReturn(Optional.of(intent));

        PaymentExternalExecutionProcessor processor = new PaymentExternalExecutionProcessor(
                outboxRepository,
                intentRepository,
                walletRepository,
                ledgerService,
                auditService,
                new PaymentStateMachine(),
                List.of(executorReturning(PaymentRailExecutor.ExecutionResult.retryableFailure(
                        "PAYMENT_PROVIDER_BUSY",
                        "Provider temporariamente indisponivel."))));

        processor.process(outbox.getId());

        assertEquals(PaymentEnums.PaymentIntentStatus.PROCESSING, intent.getStatus());
        assertEquals("FAILED_RETRYABLE", outbox.getStatus());
        assertEquals("PAYMENT_PROVIDER_BUSY: Provider temporariamente indisponivel.", outbox.getLastError());
        assertNull(outbox.getClaimedBy());
        verify(ledgerService, never()).updateBalance(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unclaimedOutboxIsIgnored() {
        PaymentExecutionOutboxRepository outboxRepository = mock(PaymentExecutionOutboxRepository.class);
        PaymentIntentRepository intentRepository = mock(PaymentIntentRepository.class);
        WalletRepository walletRepository = mock(WalletRepository.class);
        LedgerContract ledgerService = mock(LedgerContract.class);
        PaymentAuditService auditService = mock(PaymentAuditService.class);
        PaymentIntentEntity intent = processingIntent();
        PaymentExecutionOutboxEntity outbox = outbox(intent);
        outbox.setStatus("PENDING");
        outbox.setClaimedBy(null);
        outbox.setClaimedAt(null);
        when(outboxRepository.findByIdForUpdate(outbox.getId())).thenReturn(Optional.of(outbox));

        PaymentExternalExecutionProcessor processor = new PaymentExternalExecutionProcessor(
                outboxRepository,
                intentRepository,
                walletRepository,
                ledgerService,
                auditService,
                new PaymentStateMachine(),
                List.of(executorReturning(PaymentRailExecutor.ExecutionResult.accepted("payment-ref", "ACCEPTED"))));

        processor.process(outbox.getId());

        assertEquals("PENDING", outbox.getStatus());
        verifyNoInteractions(intentRepository);
        verifyNoInteractions(ledgerService);
    }

    private PaymentRailExecutor executorReturning(PaymentRailExecutor.ExecutionResult result) {
        return new PaymentRailExecutor() {
            @Override
            public PaymentEnums.PaymentRail rail() {
                return PaymentEnums.PaymentRail.LIGHTNING;
            }

            @Override
            public ExecutionResult execute(PaymentIntentEntity intent) {
                return result;
            }
        };
    }

    private PaymentIntentEntity processingIntent() {
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
        intent.setStatus(PaymentEnums.PaymentIntentStatus.PROCESSING);
        return intent;
    }

    private PaymentExecutionOutboxEntity outbox(PaymentIntentEntity intent) {
        PaymentExecutionOutboxEntity outbox = new PaymentExecutionOutboxEntity();
        outbox.setPaymentIntentId(intent.getId());
        outbox.setRail(intent.getRail().name());
        outbox.setIdempotencyKey("idem-ext");
        outbox.setStatus("PROCESSING");
        outbox.setClaimedBy("worker-1");
        outbox.setClaimedAt(Instant.now().minusSeconds(60));
        outbox.setNextAttemptAt(Instant.now().minusSeconds(60));
        return outbox;
    }
}
