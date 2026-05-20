package source.transactions.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import source.common.observability.FinancialOperationsMetrics;
import source.transactions.application.externalpayments.ExternalPaymentsLedgerPort;
import source.transactions.infra.BlockchainClient;
import source.transactions.model.ExternalProviderOutboxEntity;
import source.transactions.model.ExternalTransferEntity;
import source.transactions.model.FinancialReconciliationIssueEntity;
import source.transactions.model.FinancialReconciliationRunEntity;
import source.transactions.repository.ExternalTransferRepository;
import source.transactions.repository.FinancialReconciliationIssueRepository;
import source.transactions.repository.FinancialReconciliationRunRepository;
import source.treasury.service.FinancialAuditTrailService;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancialReconciliationServiceTest {

    @Test
    void marksCompletedOnchainTransferAutoResolutionPendingWhenConfirmationsRegress() throws Exception {
        ExternalTransferRepository transferRepository = mock(ExternalTransferRepository.class);
        FinancialReconciliationRunRepository runRepository = mock(FinancialReconciliationRunRepository.class);
        FinancialReconciliationIssueRepository issueRepository = mock(FinancialReconciliationIssueRepository.class);
        ExternalProviderOutboxService outboxService = mock(ExternalProviderOutboxService.class);
        NetworkTransferEventService eventService = mock(NetworkTransferEventService.class);
        BlockchainClient blockchainClient = mock(BlockchainClient.class);
        FinancialAuditTrailService auditTrailService = mock(FinancialAuditTrailService.class);
        FinancialOperationsMetrics metrics = mock(FinancialOperationsMetrics.class);
        ExternalPaymentsLedgerPort ledgerPort = mock(ExternalPaymentsLedgerPort.class);
        ProcessedTransactionService processedTransactionService = mock(ProcessedTransactionService.class);

        ExternalTransferEntity transfer = new ExternalTransferEntity();
        transfer.setId(UUID.randomUUID());
        transfer.setStatus("COMPLETED");
        transfer.setBlockchainTxid("a".repeat(64));
        transfer.setConfirmations(3);
        when(transferRepository.findTop200ByStatusInOrderByUpdatedAtAsc(anyCollection()))
                .thenReturn(List.of(transfer));
        when(runRepository.save(any(FinancialReconciliationRunEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(issueRepository.save(any(FinancialReconciliationIssueEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxService.findDueForAutomaticResolution()).thenReturn(List.of());
        when(blockchainClient.getRawTransaction("a".repeat(64), true))
                .thenReturn(new ObjectMapper().readTree("{\"confirmations\":1}"));

        FinancialReconciliationService service = new FinancialReconciliationService(
                transferRepository,
                runRepository,
                issueRepository,
                outboxService,
                eventService,
                blockchainClient,
                auditTrailService,
                metrics,
                ledgerPort,
                processedTransactionService);

        FinancialReconciliationRunEntity run = service.runOnce();

        assertEquals("AUTO_RESOLUTION_PENDING", transfer.getStatus());
        assertEquals(1, run.getIssueCount());
        verify(issueRepository).save(any(FinancialReconciliationIssueEntity.class));
        verify(eventService).warn(any(ExternalTransferEntity.class), any(), any(), any());
    }

    @Test
    void skipsConfirmationCheckWhenPrunedNodeCannotServeOldRawTransaction() {
        ExternalTransferRepository transferRepository = mock(ExternalTransferRepository.class);
        FinancialReconciliationRunRepository runRepository = mock(FinancialReconciliationRunRepository.class);
        FinancialReconciliationIssueRepository issueRepository = mock(FinancialReconciliationIssueRepository.class);
        ExternalProviderOutboxService outboxService = mock(ExternalProviderOutboxService.class);
        NetworkTransferEventService eventService = mock(NetworkTransferEventService.class);
        BlockchainClient blockchainClient = mock(BlockchainClient.class);
        FinancialAuditTrailService auditTrailService = mock(FinancialAuditTrailService.class);
        FinancialOperationsMetrics metrics = mock(FinancialOperationsMetrics.class);
        ExternalPaymentsLedgerPort ledgerPort = mock(ExternalPaymentsLedgerPort.class);
        ProcessedTransactionService processedTransactionService = mock(ProcessedTransactionService.class);

        String txid = "d".repeat(64);
        ExternalTransferEntity transfer = new ExternalTransferEntity();
        transfer.setId(UUID.randomUUID());
        transfer.setStatus("COMPLETED");
        transfer.setBlockchainTxid(txid);
        transfer.setConfirmations(6);
        when(transferRepository.findTop200ByStatusInOrderByUpdatedAtAsc(anyCollection()))
                .thenReturn(List.of(transfer));
        when(runRepository.save(any(FinancialReconciliationRunEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(issueRepository.save(any(FinancialReconciliationIssueEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxService.findDueForAutomaticResolution()).thenReturn(List.of());
        when(blockchainClient.getRawTransaction(txid, true)).thenThrow(new IllegalStateException(
                "Bitcoin Core RPC request failed for method getrawtransaction",
                new IllegalStateException(
                        "Bitcoin Core RPC getrawtransaction failed: No such mempool or blockchain transaction. "
                                + "Use -txindex or provide a block hash to enable blockchain transaction queries.")));

        FinancialReconciliationService service = new FinancialReconciliationService(
                transferRepository,
                runRepository,
                issueRepository,
                outboxService,
                eventService,
                blockchainClient,
                auditTrailService,
                metrics,
                ledgerPort,
                processedTransactionService);

        FinancialReconciliationRunEntity run = service.runOnce();

        assertEquals("OK", run.getStatus());
        assertEquals(0, run.getIssueCount());
        verify(issueRepository, never()).save(any(FinancialReconciliationIssueEntity.class));
        verify(eventService, never()).warn(any(ExternalTransferEntity.class), any(), any(), any());
    }

    @Test
    void autoRefundsProviderFailureWithoutExternalReferenceOnce() {
        ExternalTransferRepository transferRepository = mock(ExternalTransferRepository.class);
        FinancialReconciliationRunRepository runRepository = mock(FinancialReconciliationRunRepository.class);
        FinancialReconciliationIssueRepository issueRepository = mock(FinancialReconciliationIssueRepository.class);
        ExternalProviderOutboxService outboxService = mock(ExternalProviderOutboxService.class);
        NetworkTransferEventService eventService = mock(NetworkTransferEventService.class);
        BlockchainClient blockchainClient = mock(BlockchainClient.class);
        FinancialAuditTrailService auditTrailService = mock(FinancialAuditTrailService.class);
        FinancialOperationsMetrics metrics = mock(FinancialOperationsMetrics.class);
        ExternalPaymentsLedgerPort ledgerPort = mock(ExternalPaymentsLedgerPort.class);
        ProcessedTransactionService processedTransactionService = mock(ProcessedTransactionService.class);
        ExternalTransferEntity transfer = new ExternalTransferEntity();
        transfer.setId(UUID.randomUUID());
        transfer.setStatus("PROVIDER_FAILED");
        transfer.setWalletId(10L);
        transfer.setUserId(1L);
        transfer.setTotalDebitedBtc(new BigDecimal("0.01000000"));
        when(transferRepository.findTop200ByStatusInOrderByUpdatedAtAsc(anyCollection()))
                .thenReturn(List.of(transfer));
        when(runRepository.save(any(FinancialReconciliationRunEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(issueRepository.save(any(FinancialReconciliationIssueEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            Runnable processor = invocation.getArgument(2);
            processor.run();
            return true;
        }).when(processedTransactionService).processOnce(anyString(), eq("EXTERNAL_PROVIDER_FINAL_REFUND"), any(Runnable.class));

        FinancialReconciliationService service = new FinancialReconciliationService(
                transferRepository,
                runRepository,
                issueRepository,
                outboxService,
                eventService,
                blockchainClient,
                auditTrailService,
                metrics,
                ledgerPort,
                processedTransactionService);

        FinancialReconciliationRunEntity run = service.runOnce();

        assertEquals("FAILED_SAFE", transfer.getStatus());
        assertEquals(1, run.getIssueCount());
        verify(ledgerPort).updateBalance(
                10L,
                new BigDecimal("0.01000000"),
                "EXTERNAL_PROVIDER_FINAL_REFUND:" + transfer.getId());
        verify(eventService).info(
                transfer,
                "RECONCILIATION_PROVIDER_AUTO_REFUNDED",
                transfer.getId().toString(),
                "amountBtc=0.01000000");
    }

    @Test
    void providerFailureWithExternalReferenceRequiresManualResolution() {
        ExternalTransferRepository transferRepository = mock(ExternalTransferRepository.class);
        FinancialReconciliationRunRepository runRepository = mock(FinancialReconciliationRunRepository.class);
        FinancialReconciliationIssueRepository issueRepository = mock(FinancialReconciliationIssueRepository.class);
        ExternalProviderOutboxService outboxService = mock(ExternalProviderOutboxService.class);
        NetworkTransferEventService eventService = mock(NetworkTransferEventService.class);
        BlockchainClient blockchainClient = mock(BlockchainClient.class);
        FinancialAuditTrailService auditTrailService = mock(FinancialAuditTrailService.class);
        FinancialOperationsMetrics metrics = mock(FinancialOperationsMetrics.class);
        ExternalPaymentsLedgerPort ledgerPort = mock(ExternalPaymentsLedgerPort.class);
        ProcessedTransactionService processedTransactionService = mock(ProcessedTransactionService.class);
        ExternalTransferEntity transfer = new ExternalTransferEntity();
        transfer.setId(UUID.randomUUID());
        transfer.setStatus("PROVIDER_FAILED");
        transfer.setExternalReference("provider-ref");
        transfer.setWalletId(10L);
        transfer.setTotalDebitedBtc(new BigDecimal("0.01000000"));
        when(transferRepository.findTop200ByStatusInOrderByUpdatedAtAsc(anyCollection()))
                .thenReturn(List.of(transfer));
        when(runRepository.save(any(FinancialReconciliationRunEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(issueRepository.save(any(FinancialReconciliationIssueEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FinancialReconciliationService service = new FinancialReconciliationService(
                transferRepository,
                runRepository,
                issueRepository,
                outboxService,
                eventService,
                blockchainClient,
                auditTrailService,
                metrics,
                ledgerPort,
                processedTransactionService);

        service.runOnce();

        assertEquals("AUTO_RESOLUTION_PENDING", transfer.getStatus());
        verify(eventService).warn(
                transfer,
                "RECONCILIATION_PROVIDER_FAILURE_AMBIGUOUS",
                "provider-ref",
                "Provider failure has an external reference and requires manual resolution.");
    }

    @Test
    void reversesCreditedInboundWhenConfirmationsRegressAndBalanceIsAvailable() throws Exception {
        ExternalTransferRepository transferRepository = mock(ExternalTransferRepository.class);
        FinancialReconciliationRunRepository runRepository = mock(FinancialReconciliationRunRepository.class);
        FinancialReconciliationIssueRepository issueRepository = mock(FinancialReconciliationIssueRepository.class);
        ExternalProviderOutboxService outboxService = mock(ExternalProviderOutboxService.class);
        NetworkTransferEventService eventService = mock(NetworkTransferEventService.class);
        BlockchainClient blockchainClient = mock(BlockchainClient.class);
        FinancialAuditTrailService auditTrailService = mock(FinancialAuditTrailService.class);
        FinancialOperationsMetrics metrics = mock(FinancialOperationsMetrics.class);
        ExternalPaymentsLedgerPort ledgerPort = mock(ExternalPaymentsLedgerPort.class);
        ProcessedTransactionService processedTransactionService = mock(ProcessedTransactionService.class);
        ExternalTransferEntity transfer = new ExternalTransferEntity();
        transfer.setId(UUID.randomUUID());
        transfer.setStatus("COMPLETED");
        transfer.setTransferType("INBOUND_DEPOSIT");
        transfer.setWalletId(10L);
        transfer.setBlockchainTxid("b".repeat(64));
        transfer.setConfirmations(3);
        transfer.setAmountBtc(new BigDecimal("0.01000000"));
        transfer.setPlatformFeeBtc(new BigDecimal("0.00010000"));
        when(transferRepository.findTop200ByStatusInOrderByUpdatedAtAsc(anyCollection()))
                .thenReturn(List.of(transfer));
        when(runRepository.save(any(FinancialReconciliationRunEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(issueRepository.save(any(FinancialReconciliationIssueEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(blockchainClient.getRawTransaction("b".repeat(64), true))
                .thenReturn(new ObjectMapper().readTree("{\"confirmations\":1}"));
        doAnswer(invocation -> {
            Runnable processor = invocation.getArgument(2);
            processor.run();
            return true;
        }).when(processedTransactionService).processOnce(anyString(), eq("CONFIRMATION_REGRESSION_REVERSAL"), any(Runnable.class));

        FinancialReconciliationService service = new FinancialReconciliationService(
                transferRepository,
                runRepository,
                issueRepository,
                outboxService,
                eventService,
                blockchainClient,
                auditTrailService,
                metrics,
                ledgerPort,
                processedTransactionService);

        FinancialReconciliationRunEntity run = service.runOnce();

        assertEquals("FAILED_SAFE", transfer.getStatus());
        assertEquals(1, transfer.getConfirmations());
        assertEquals(1, run.getIssueCount());
        verify(ledgerPort).ensureBalance(10L, new BigDecimal("0.00990000"));
        verify(ledgerPort).updateBalance(
                10L,
                new BigDecimal("-0.00990000"),
                "CONFIRMATION_REGRESSION_REVERSAL:" + transfer.getId());
        verify(eventService).info(
                transfer,
                "RECONCILIATION_CONFIRMATION_REGRESSION_REVERSED",
                "b".repeat(64),
                "amountBtc=0.00990000 | observedConfirmations=1 | storedConfirmations=3");
    }

    @Test
    void confirmationRegressionAfterCreditStaysManualWhenBalanceCannotBeDebited() throws Exception {
        ExternalTransferRepository transferRepository = mock(ExternalTransferRepository.class);
        FinancialReconciliationRunRepository runRepository = mock(FinancialReconciliationRunRepository.class);
        FinancialReconciliationIssueRepository issueRepository = mock(FinancialReconciliationIssueRepository.class);
        ExternalProviderOutboxService outboxService = mock(ExternalProviderOutboxService.class);
        NetworkTransferEventService eventService = mock(NetworkTransferEventService.class);
        BlockchainClient blockchainClient = mock(BlockchainClient.class);
        FinancialAuditTrailService auditTrailService = mock(FinancialAuditTrailService.class);
        FinancialOperationsMetrics metrics = mock(FinancialOperationsMetrics.class);
        ExternalPaymentsLedgerPort ledgerPort = mock(ExternalPaymentsLedgerPort.class);
        ProcessedTransactionService processedTransactionService = mock(ProcessedTransactionService.class);
        ExternalTransferEntity transfer = new ExternalTransferEntity();
        transfer.setId(UUID.randomUUID());
        transfer.setStatus("COMPLETED");
        transfer.setTransferType("INBOUND_DEPOSIT");
        transfer.setWalletId(10L);
        transfer.setBlockchainTxid("c".repeat(64));
        transfer.setConfirmations(6);
        transfer.setAmountBtc(new BigDecimal("0.02000000"));
        transfer.setPlatformFeeBtc(new BigDecimal("0.00020000"));
        when(transferRepository.findTop200ByStatusInOrderByUpdatedAtAsc(anyCollection()))
                .thenReturn(List.of(transfer));
        when(runRepository.save(any(FinancialReconciliationRunEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(issueRepository.save(any(FinancialReconciliationIssueEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(blockchainClient.getRawTransaction("c".repeat(64), true))
                .thenReturn(new ObjectMapper().readTree("{\"confirmations\":2}"));
        doThrow(new source.ledger.exceptions.LedgerExceptions.InsufficientBalanceException("insufficient"))
                .when(ledgerPort).ensureBalance(10L, new BigDecimal("0.01980000"));

        FinancialReconciliationService service = new FinancialReconciliationService(
                transferRepository,
                runRepository,
                issueRepository,
                outboxService,
                eventService,
                blockchainClient,
                auditTrailService,
                metrics,
                ledgerPort,
                processedTransactionService);

        service.runOnce();

        assertEquals("AUTO_RESOLUTION_PENDING", transfer.getStatus());
        assertEquals(2, transfer.getConfirmations());
        verify(processedTransactionService, never()).processOnce(anyString(), eq("CONFIRMATION_REGRESSION_REVERSAL"), any(Runnable.class));
        verify(ledgerPort, never()).updateBalance(any(), any(), anyString());
    }

    @Test
    void staleProviderPendingWithRetryableOutboxKeepsTransferPendingForWorker() {
        ExternalTransferRepository transferRepository = mock(ExternalTransferRepository.class);
        FinancialReconciliationRunRepository runRepository = mock(FinancialReconciliationRunRepository.class);
        FinancialReconciliationIssueRepository issueRepository = mock(FinancialReconciliationIssueRepository.class);
        ExternalProviderOutboxService outboxService = mock(ExternalProviderOutboxService.class);
        NetworkTransferEventService eventService = mock(NetworkTransferEventService.class);
        BlockchainClient blockchainClient = mock(BlockchainClient.class);
        FinancialAuditTrailService auditTrailService = mock(FinancialAuditTrailService.class);
        FinancialOperationsMetrics metrics = mock(FinancialOperationsMetrics.class);
        ExternalPaymentsLedgerPort ledgerPort = mock(ExternalPaymentsLedgerPort.class);
        ProcessedTransactionService processedTransactionService = mock(ProcessedTransactionService.class);
        ExternalTransferEntity transfer = new ExternalTransferEntity();
        transfer.setId(UUID.randomUUID());
        transfer.setStatus("PROVIDER_PENDING");
        transfer.setUpdatedAt(LocalDateTime.now().minusMinutes(30));
        ExternalProviderOutboxEntity outbox = new ExternalProviderOutboxEntity();
        outbox.setTransferId(transfer.getId());
        outbox.setStatus("FAILED_RETRYABLE");
        outbox.setAttempts(2);
        outbox.setIdempotencyKey("idem-retry");
        outbox.setNextAttemptAt(LocalDateTime.now().minusMinutes(1));
        when(transferRepository.findTop200ByStatusInOrderByUpdatedAtAsc(anyCollection()))
                .thenReturn(List.of(transfer));
        when(runRepository.save(any(FinancialReconciliationRunEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(issueRepository.save(any(FinancialReconciliationIssueEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxService.findLatestByTransferId(transfer.getId())).thenReturn(java.util.Optional.of(outbox));
        when(outboxService.findDueForAutomaticResolution()).thenReturn(List.of());

        FinancialReconciliationService service = new FinancialReconciliationService(
                transferRepository,
                runRepository,
                issueRepository,
                outboxService,
                eventService,
                blockchainClient,
                auditTrailService,
                metrics,
                ledgerPort,
                processedTransactionService);

        FinancialReconciliationRunEntity run = service.runOnce();

        assertEquals("PROVIDER_PENDING", transfer.getStatus());
        assertEquals(1, run.getIssueCount());
        verify(eventService).warn(
                transfer,
                "RECONCILIATION_PROVIDER_RETRY_SCHEDULED",
                "idem-retry",
                "Provider outbox worker will retry status=FAILED_RETRYABLE attempts=2");
        verify(ledgerPort, never()).updateBalance(any(), any(), anyString());
    }

    @Test
    void staleProviderPendingWithFinalOutboxFailureAutoRefundsWhenSafe() {
        ExternalTransferRepository transferRepository = mock(ExternalTransferRepository.class);
        FinancialReconciliationRunRepository runRepository = mock(FinancialReconciliationRunRepository.class);
        FinancialReconciliationIssueRepository issueRepository = mock(FinancialReconciliationIssueRepository.class);
        ExternalProviderOutboxService outboxService = mock(ExternalProviderOutboxService.class);
        NetworkTransferEventService eventService = mock(NetworkTransferEventService.class);
        BlockchainClient blockchainClient = mock(BlockchainClient.class);
        FinancialAuditTrailService auditTrailService = mock(FinancialAuditTrailService.class);
        FinancialOperationsMetrics metrics = mock(FinancialOperationsMetrics.class);
        ExternalPaymentsLedgerPort ledgerPort = mock(ExternalPaymentsLedgerPort.class);
        ProcessedTransactionService processedTransactionService = mock(ProcessedTransactionService.class);
        ExternalTransferEntity transfer = new ExternalTransferEntity();
        transfer.setId(UUID.randomUUID());
        transfer.setStatus("PROVIDER_PENDING");
        transfer.setUpdatedAt(LocalDateTime.now().minusMinutes(30));
        transfer.setWalletId(10L);
        transfer.setTotalDebitedBtc(new BigDecimal("0.01000000"));
        ExternalProviderOutboxEntity outbox = new ExternalProviderOutboxEntity();
        outbox.setTransferId(transfer.getId());
        outbox.setStatus("FAILED_FINAL");
        outbox.setIdempotencyKey("idem-final");
        outbox.setNextAttemptAt(LocalDateTime.now().minusMinutes(1));
        when(transferRepository.findTop200ByStatusInOrderByUpdatedAtAsc(anyCollection()))
                .thenReturn(List.of(transfer));
        when(runRepository.save(any(FinancialReconciliationRunEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(issueRepository.save(any(FinancialReconciliationIssueEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxService.findLatestByTransferId(transfer.getId())).thenReturn(java.util.Optional.of(outbox));
        when(outboxService.findDueForAutomaticResolution()).thenReturn(List.of());
        doAnswer(invocation -> {
            Runnable processor = invocation.getArgument(2);
            processor.run();
            return true;
        }).when(processedTransactionService).processOnce(anyString(), eq("EXTERNAL_PROVIDER_FINAL_REFUND"), any(Runnable.class));

        FinancialReconciliationService service = new FinancialReconciliationService(
                transferRepository,
                runRepository,
                issueRepository,
                outboxService,
                eventService,
                blockchainClient,
                auditTrailService,
                metrics,
                ledgerPort,
                processedTransactionService);

        service.runOnce();

        assertEquals("FAILED_SAFE", transfer.getStatus());
        verify(ledgerPort).updateBalance(
                10L,
                new BigDecimal("0.01000000"),
                "EXTERNAL_PROVIDER_FINAL_REFUND:" + transfer.getId());
    }
}
