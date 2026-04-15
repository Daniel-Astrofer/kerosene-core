package source.transactions.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.security.VaultKeyProvider;
import source.transactions.application.externalpayments.ExternalPaymentsLedgerPort;
import source.transactions.application.externalpayments.ExternalPaymentsMath;
import source.transactions.application.externalpayments.ExternalPaymentsNotificationPort;
import source.transactions.application.externalpayments.ExternalTransfersPort;
import source.transactions.infra.BlockchainClient;
import source.transactions.infra.CustodyGateway;
import source.transactions.model.ExternalTransferEntity;
import source.wallet.service.WalletCardProfileService;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private ExternalPaymentsLedgerPort ledgerPort;

    @Mock
    private ExternalPaymentsNotificationPort notificationPort;

    @Mock
    private WalletCardProfileService walletCardProfileService;

    @Mock
    private BlockchainClient blockchainClient;

    @Mock
    private CustodyGateway custodyGateway;

    @Mock
    private VaultKeyProvider vaultKeyProvider;

    @Test
    void creditsConfirmedOnchainInboundTransfer() {
        InboundTransferMonitorService service = new InboundTransferMonitorService(
                externalTransfersPort,
                ledgerPort,
                notificationPort,
                new ExternalPaymentsMath(),
                walletCardProfileService,
                blockchainClient,
                custodyGateway,
                vaultKeyProvider,
                200);

        ExternalTransferEntity transfer = new ExternalTransferEntity();
        transfer.setUserId(11L);
        transfer.setWalletId(19L);
        transfer.setWalletNameSnapshot("MAIN");
        transfer.setTransferType("ONRAMP_PURCHASE");
        transfer.setStatus("PENDING");
        transfer.setProvider("ONRAMP");
        transfer.setDestination("bc1qdedicatedmonitoraddress000000000000000000000");

        when(vaultKeyProvider.isReady()).thenReturn(true);
        when(externalTransfersPort.findInboundTransfersForMonitoring(200)).thenReturn(List.of(transfer));
        when(blockchainClient.getConfirmedBalanceForAddress(transfer.getDestination())).thenReturn(1_500_000L);
        when(walletCardProfileService.calculateDepositFee(eq(11L), eq(new BigDecimal("0.01500000"))))
                .thenReturn(new BigDecimal("0.00012000"));
        when(externalTransfersPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.monitorInboundTransfers();

        verify(ledgerPort).updateBalance(19L, new BigDecimal("0.01488000"), "INBOUND_TRANSFER:" + transfer.getId());
        ArgumentCaptor<ExternalTransferEntity> transferCaptor = ArgumentCaptor.forClass(ExternalTransferEntity.class);
        verify(externalTransfersPort).save(transferCaptor.capture());
        assertEquals("COMPLETED", transferCaptor.getValue().getStatus());
        assertEquals(new BigDecimal("0.01500000"), transferCaptor.getValue().getAmountBtc());
        assertEquals(new BigDecimal("0.00012000"), transferCaptor.getValue().getPlatformFeeBtc());
    }

    @Test
    void skipsMonitoringWhileVaultIsInStallMode() {
        InboundTransferMonitorService service = new InboundTransferMonitorService(
                externalTransfersPort,
                ledgerPort,
                notificationPort,
                new ExternalPaymentsMath(),
                walletCardProfileService,
                blockchainClient,
                custodyGateway,
                vaultKeyProvider,
                200);

        when(vaultKeyProvider.isReady()).thenReturn(false);

        service.monitorInboundTransfers();

        verify(externalTransfersPort, never()).findInboundTransfersForMonitoring(any(Integer.class));
    }
}
