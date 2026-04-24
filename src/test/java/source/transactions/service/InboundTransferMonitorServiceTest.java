package source.transactions.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.security.VaultKeyProvider;
import source.transactions.application.externalpayments.ExternalPaymentsMath;
import source.transactions.application.externalpayments.ExternalTransfersPort;
import source.transactions.infra.BlockchainClient;
import source.transactions.infra.CustodyGateway;
import source.transactions.model.BlockchainAddressWatchEntity;
import source.transactions.model.ExternalTransferEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboundTransferMonitorServiceTest {

    @Mock
    private ExternalTransfersPort externalTransfersPort;

    @Mock
    private BlockchainClient blockchainClient;

    @Mock
    private CustodyGateway custodyGateway;

    @Mock
    private VaultKeyProvider vaultKeyProvider;

    @Mock
    private BlockchainAddressWatchService blockchainAddressWatchService;

    @Mock
    private NetworkTransferLifecycleService networkTransferLifecycleService;

    @Test
    void reconcilesTrackedOnchainTransferUsingKnownTxid() {
        InboundTransferMonitorService service = new InboundTransferMonitorService(
                externalTransfersPort,
                new ExternalPaymentsMath("testnet"),
                blockchainClient,
                custodyGateway,
                vaultKeyProvider,
                blockchainAddressWatchService,
                networkTransferLifecycleService,
                200);

        UUID transferId = UUID.randomUUID();
        ExternalTransferEntity transfer = new ExternalTransferEntity();
        transfer.setId(transferId);
        transfer.setUserId(11L);
        transfer.setWalletId(19L);
        transfer.setWalletNameSnapshot("MAIN");
        transfer.setTransferType("ONRAMP_PURCHASE");
        transfer.setStatus("PENDING");
        transfer.setDestination("tb1qdedicatedmonitoraddress000000000000000000000");
        transfer.setBlockchainTxid("txid-123");
        transfer.setAmountBtc(new BigDecimal("0.01500000"));

        BlockchainAddressWatchEntity watch = new BlockchainAddressWatchEntity();
        watch.setTransferId(transferId);
        watch.setAddress(transfer.getDestination());
        watch.setObservedAmountSats(1_500_000L);

        ObjectNode rawTransaction = new ObjectMapper().createObjectNode();
        rawTransaction.put("confirmations", 3);

        ExternalTransferEntity settled = new ExternalTransferEntity();
        settled.setId(transferId);
        settled.setStatus("COMPLETED");

        when(vaultKeyProvider.isReady()).thenReturn(true);
        when(externalTransfersPort.findInboundTransfersForMonitoring(200)).thenReturn(List.of(transfer));
        when(blockchainAddressWatchService.findByTransferId(transferId)).thenReturn(Optional.of(watch));
        when(blockchainClient.getRawTransaction("txid-123", true)).thenReturn(rawTransaction);
        when(networkTransferLifecycleService.reconcileOnchainSettlement(
                eq(transfer),
                eq(1_500_000L),
                eq("txid-123"),
                eq(3),
                eq("INBOUND_MONITOR"))).thenReturn(settled);

        service.monitorInboundTransfers();

        verify(blockchainAddressWatchService).markDetected(watch, "txid-123", 1_500_000L, 3);
        verify(blockchainAddressWatchService).markCompleted(watch, 3);
        verify(networkTransferLifecycleService).reconcileOnchainSettlement(
                transfer,
                1_500_000L,
                "txid-123",
                3,
                "INBOUND_MONITOR");
    }

    @Test
    void skipsMonitoringWhileVaultIsInStallMode() {
        InboundTransferMonitorService service = new InboundTransferMonitorService(
                externalTransfersPort,
                new ExternalPaymentsMath("testnet"),
                blockchainClient,
                custodyGateway,
                vaultKeyProvider,
                blockchainAddressWatchService,
                networkTransferLifecycleService,
                200);

        when(vaultKeyProvider.isReady()).thenReturn(false);

        service.monitorInboundTransfers();

        verify(externalTransfersPort, never()).findInboundTransfersForMonitoring(any(Integer.class));
    }
}
