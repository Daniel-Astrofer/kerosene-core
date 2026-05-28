package source.transactions.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import source.common.observability.FinancialOperationsMetrics;
import source.transactions.application.externalpayments.ExternalPaymentsCustodyPort;
import source.transactions.application.externalpayments.ExternalPaymentsMath;
import source.transactions.infra.CustodyGateway;
import source.transactions.model.ExternalProviderOutboxEntity;
import source.transactions.model.ExternalTransferEntity;
import source.transactions.repository.ExternalProviderOutboxRepository;
import source.transactions.repository.ExternalTransferRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalProviderOutboxProcessorTest {

    @Test
    void onchainRetrySuccessDispatchesClaimedOutboxAndUpdatesTransfer() {
        ExternalProviderOutboxRepository outboxRepository = mock(ExternalProviderOutboxRepository.class);
        ExternalTransferRepository transferRepository = mock(ExternalTransferRepository.class);
        ExternalPaymentsCustodyPort onchainCustodyPort = mock(ExternalPaymentsCustodyPort.class);
        CustodyGateway custodyGateway = mock(CustodyGateway.class);
        NetworkTransferEventService eventService = mock(NetworkTransferEventService.class);
        FinancialOperationsMetrics metrics = mock(FinancialOperationsMetrics.class);
        ExternalProviderOutboxEntity outbox = claimedOutbox("ONCHAIN_SEND");
        outbox.setStatus("PROCESSING");
        outbox.setAttempts(1);
        outbox.setPayloadJson("{\"destination\":\"tb1qdestination\",\"amountSats\":25000}");
        ExternalTransferEntity transfer = transfer("ONCHAIN");
        when(outboxRepository.findByIdForUpdate(outbox.getId())).thenReturn(Optional.of(outbox));
        when(transferRepository.findById(outbox.getTransferId())).thenReturn(Optional.of(transfer));
        when(onchainCustodyPort.sendOnchain(any())).thenReturn(new ExternalPaymentsCustodyPort.PaymentResult(
                "provider-ref",
                "txid-123",
                null,
                "MEMPOOL",
                120L,
                "raw"));

        processor(outboxRepository, transferRepository, onchainCustodyPort, custodyGateway, eventService, metrics)
                .process(outbox.getId());

        assertEquals("DISPATCHED", outbox.getStatus());
        assertEquals("txid-123", outbox.getProviderReference());
        assertNull(outbox.getClaimedBy());
        assertEquals("MEMPOOL", transfer.getStatus());
        assertEquals("txid-123", transfer.getBlockchainTxid());
        assertEquals(new BigDecimal("0.00000120"), transfer.getNetworkFeeBtc());

        ArgumentCaptor<ExternalPaymentsCustodyPort.OnchainPaymentCommand> commandCaptor =
                ArgumentCaptor.forClass(ExternalPaymentsCustodyPort.OnchainPaymentCommand.class);
        verify(onchainCustodyPort).sendOnchain(commandCaptor.capture());
        assertEquals("idem-provider-1", commandCaptor.getValue().idempotencyKey());
        verify(eventService).info(transfer, "PROVIDER_OUTBOX_DISPATCHED", "txid-123",
                "operationType=ONCHAIN_SEND | idempotencyKey="
                        + source.common.infra.logging.LogSanitizer.fingerprint("idem-provider-1"));
    }

    @Test
    void lightningPayDispatchesClaimedOutboxAndSetsSettledAt() {
        ExternalProviderOutboxRepository outboxRepository = mock(ExternalProviderOutboxRepository.class);
        ExternalTransferRepository transferRepository = mock(ExternalTransferRepository.class);
        ExternalPaymentsCustodyPort onchainCustodyPort = mock(ExternalPaymentsCustodyPort.class);
        CustodyGateway custodyGateway = mock(CustodyGateway.class);
        NetworkTransferEventService eventService = mock(NetworkTransferEventService.class);
        FinancialOperationsMetrics metrics = mock(FinancialOperationsMetrics.class);
        ExternalProviderOutboxEntity outbox = claimedOutbox("LIGHTNING_PAY");
        outbox.setPayloadJson("{\"amountSats\":30000,\"maxFeeSats\":1200}");
        ExternalTransferEntity transfer = transfer("LIGHTNING");
        transfer.setDestination("lnbcinvoice");
        when(outboxRepository.findByIdForUpdate(outbox.getId())).thenReturn(Optional.of(outbox));
        when(transferRepository.findById(outbox.getTransferId())).thenReturn(Optional.of(transfer));
        when(custodyGateway.payLightning(any())).thenReturn(new CustodyGateway.PaymentResult(
                "provider-ref",
                null,
                "payment-hash-1",
                "SETTLED",
                900L,
                "raw"));

        processor(outboxRepository, transferRepository, onchainCustodyPort, custodyGateway, eventService, metrics)
                .process(outbox.getId());

        assertEquals("DISPATCHED", outbox.getStatus());
        assertEquals("payment-hash-1", outbox.getProviderReference());
        assertEquals("SETTLED", transfer.getStatus());
        assertEquals("payment-hash-1", transfer.getPaymentHash());
        assertEquals(new BigDecimal("0.00000900"), transfer.getNetworkFeeBtc());
        verify(custodyGateway).payLightning(any());
    }

    @Test
    void missingTransferFailsOutboxWithoutCallingProvider() {
        ExternalProviderOutboxRepository outboxRepository = mock(ExternalProviderOutboxRepository.class);
        ExternalTransferRepository transferRepository = mock(ExternalTransferRepository.class);
        ExternalPaymentsCustodyPort onchainCustodyPort = mock(ExternalPaymentsCustodyPort.class);
        CustodyGateway custodyGateway = mock(CustodyGateway.class);
        NetworkTransferEventService eventService = mock(NetworkTransferEventService.class);
        FinancialOperationsMetrics metrics = mock(FinancialOperationsMetrics.class);
        ExternalProviderOutboxEntity outbox = claimedOutbox("ONCHAIN_SEND");
        when(outboxRepository.findByIdForUpdate(outbox.getId())).thenReturn(Optional.of(outbox));
        when(transferRepository.findById(outbox.getTransferId())).thenReturn(Optional.empty());

        processor(outboxRepository, transferRepository, onchainCustodyPort, custodyGateway, eventService, metrics)
                .process(outbox.getId());

        assertEquals("FAILED_FINAL", outbox.getStatus());
        assertEquals("TRANSFER_NOT_FOUND: External transfer does not exist.", outbox.getLastError());
        assertNull(outbox.getClaimedBy());
        verify(onchainCustodyPort, never()).sendOnchain(any());
        verify(custodyGateway, never()).payLightning(any());
        verify(eventService).error(null, "PROVIDER_OUTBOX_FINAL_FAILURE", "idem-provider-1",
                "operationType=ONCHAIN_SEND | errorCode=TRANSFER_NOT_FOUND");
    }

    @Test
    void providerTimeoutKeepsTransferPendingAndSchedulesRetry() {
        ExternalProviderOutboxRepository outboxRepository = mock(ExternalProviderOutboxRepository.class);
        ExternalTransferRepository transferRepository = mock(ExternalTransferRepository.class);
        ExternalPaymentsCustodyPort onchainCustodyPort = mock(ExternalPaymentsCustodyPort.class);
        CustodyGateway custodyGateway = mock(CustodyGateway.class);
        NetworkTransferEventService eventService = mock(NetworkTransferEventService.class);
        FinancialOperationsMetrics metrics = mock(FinancialOperationsMetrics.class);
        ExternalProviderOutboxEntity outbox = claimedOutbox("ONCHAIN_SEND");
        outbox.setPayloadJson("{\"destination\":\"tb1qdestination\",\"amountSats\":25000}");
        ExternalTransferEntity transfer = transfer("ONCHAIN");
        when(outboxRepository.findByIdForUpdate(outbox.getId())).thenReturn(Optional.of(outbox));
        when(transferRepository.findById(outbox.getTransferId())).thenReturn(Optional.of(transfer));
        when(onchainCustodyPort.sendOnchain(any())).thenThrow(new RuntimeException("provider timeout"));

        processor(outboxRepository, transferRepository, onchainCustodyPort, custodyGateway, eventService, metrics)
                .process(outbox.getId());

        assertEquals("FAILED_RETRYABLE", outbox.getStatus());
        assertEquals(1, outbox.getAttempts());
        assertEquals("PROVIDER_PENDING", transfer.getStatus());
        assertNull(outbox.getClaimedBy());
        verify(transferRepository, never()).save(transfer);
        verify(eventService).warn(transfer, "PROVIDER_OUTBOX_RETRYABLE_FAILURE", "idem-provider-1",
                "operationType=ONCHAIN_SEND | errorCode=PROVIDER_RETRYABLE_FAILURE");
    }

    @Test
    void ambiguousOnchainBroadcastMarksOutboxUnknownAndTransferManual() {
        ExternalProviderOutboxRepository outboxRepository = mock(ExternalProviderOutboxRepository.class);
        ExternalTransferRepository transferRepository = mock(ExternalTransferRepository.class);
        ExternalPaymentsCustodyPort onchainCustodyPort = mock(ExternalPaymentsCustodyPort.class);
        CustodyGateway custodyGateway = mock(CustodyGateway.class);
        NetworkTransferEventService eventService = mock(NetworkTransferEventService.class);
        FinancialOperationsMetrics metrics = mock(FinancialOperationsMetrics.class);
        ExternalProviderOutboxEntity outbox = claimedOutbox("ONCHAIN_SEND");
        outbox.setPayloadJson("{\"destination\":\"tb1qdestination\",\"amountSats\":25000,\"maxFeeSats\":1000}");
        ExternalTransferEntity transfer = transfer("ONCHAIN");
        when(outboxRepository.findByIdForUpdate(outbox.getId())).thenReturn(Optional.of(outbox));
        when(transferRepository.findById(outbox.getTransferId())).thenReturn(Optional.of(transfer));
        when(onchainCustodyPort.sendOnchain(any())).thenThrow(new ExternalPaymentsCustodyPort.ProviderExecutionAmbiguous(
                "Bitcoin Core broadcast result is ambiguous.",
                "psbt-hash",
                "{\"status\":\"UNKNOWN\",\"combinedPsbtHash\":\"psbt-hash\"}",
                new RuntimeException("timeout")));

        processor(outboxRepository, transferRepository, onchainCustodyPort, custodyGateway, eventService, metrics)
                .process(outbox.getId());

        assertEquals("UNKNOWN", outbox.getStatus());
        assertEquals("psbt-hash", outbox.getProviderReference());
        assertNull(outbox.getClaimedBy());
        assertEquals("AUTO_RESOLUTION_PENDING", transfer.getStatus());
        verify(transferRepository).save(transfer);
        verify(eventService).warn(transfer, "PROVIDER_OUTBOX_UNKNOWN_RESULT", "idem-provider-1",
                "operationType=ONCHAIN_SEND");
    }

    private ExternalProviderOutboxProcessor processor(
            ExternalProviderOutboxRepository outboxRepository,
            ExternalTransferRepository transferRepository,
            ExternalPaymentsCustodyPort onchainCustodyPort,
            CustodyGateway custodyGateway,
            NetworkTransferEventService eventService,
            FinancialOperationsMetrics metrics) {
        return new ExternalProviderOutboxProcessor(
                outboxRepository,
                transferRepository,
                onchainCustodyPort,
                custodyGateway,
                new ExternalPaymentsMath("testnet"),
                eventService,
                metrics,
                new ObjectMapper());
    }

    private ExternalProviderOutboxEntity claimedOutbox(String operationType) {
        ExternalProviderOutboxEntity outbox = new ExternalProviderOutboxEntity();
        outbox.setTransferId(java.util.UUID.randomUUID());
        outbox.setOperationType(operationType);
        outbox.setIdempotencyKey("idem-provider-1");
        outbox.setStatus("PROCESSING");
        outbox.setClaimedBy("worker-a");
        outbox.setClaimedAt(LocalDateTime.now().minusSeconds(1));
        outbox.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        return outbox;
    }

    private ExternalTransferEntity transfer(String network) {
        ExternalTransferEntity transfer = new ExternalTransferEntity();
        transfer.setId(java.util.UUID.randomUUID());
        transfer.setUserId(1L);
        transfer.setWalletId(10L);
        transfer.setWalletNameSnapshot("MAIN");
        transfer.setNetwork(network);
        transfer.setTransferType("OUTBOUND_PAYMENT");
        transfer.setStatus("PROVIDER_PENDING");
        transfer.setProvider("KEROSENE_LOCAL");
        transfer.setDestination("tb1qdestination");
        transfer.setAmountBtc(new BigDecimal("0.00025000"));
        transfer.setNetworkFeeBtc(new BigDecimal("0.00001200"));
        return transfer;
    }
}
