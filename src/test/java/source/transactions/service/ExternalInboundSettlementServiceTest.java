package source.transactions.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.auth.application.service.account.AccountActivationService;
import source.transactions.application.externalpayments.ExternalPaymentsLedgerPort;
import source.transactions.application.externalpayments.ExternalPaymentsMath;
import source.transactions.application.externalpayments.ExternalPaymentsNotificationPort;
import source.transactions.application.externalpayments.ExternalTransfersPort;
import source.transactions.model.ExternalTransferEntity;
import source.wallet.service.WalletCardProfileService;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalInboundSettlementServiceTest {

    @Mock
    private ExternalTransfersPort externalTransfersPort;

    @Mock
    private ExternalPaymentsLedgerPort ledgerPort;

    @Mock
    private ExternalPaymentsNotificationPort notificationPort;

    @Mock
    private WalletCardProfileService walletCardProfileService;

    @Mock
    private AccountActivationService accountActivationService;

    @Mock
    private ProcessedTransactionService processedTransactionService;

    @Mock
    private NetworkTransferEventService networkTransferEventService;

    private ExternalInboundSettlementService service;

    @BeforeEach
    void setUp() {
        service = new ExternalInboundSettlementService(
                externalTransfersPort,
                ledgerPort,
                notificationPort,
                new ExternalPaymentsMath("testnet"),
                walletCardProfileService,
                accountActivationService,
                processedTransactionService,
                networkTransferEventService);
        when(externalTransfersPort.save(any(ExternalTransferEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void lightningAmountMismatchGoesToReconciliationWithoutCreditingLedger() {
        ExternalTransferEntity transfer = lightningTransfer();
        when(processedTransactionService.processOnce(eq("hash-mismatch"), eq("INBOUND_LIGHTNING"), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    Runnable processor = invocation.getArgument(2);
                    processor.run();
                    return true;
                });

        boolean settled = service.settleLightningInbound(
                transfer,
                110_000L,
                "hash-mismatch",
                "settled");

        assertFalse(settled);
        assertEquals("AUTO_RESOLUTION_PENDING", transfer.getStatus());
        assertEquals(new BigDecimal("0.00110000"), transfer.getAmountBtc());
        verify(ledgerPort, never()).updateBalance(any(), any(), anyString());
        verify(ledgerPort, never()).recordHistory(any());
        verify(notificationPort, never()).notifyUser(any(), any());
        verify(accountActivationService, never()).activateUser(any());
        verify(networkTransferEventService).warn(
                eq(transfer),
                eq("INBOUND_AMOUNT_MISMATCH"),
                eq("hash-mismatch"),
                eq("expected=0.00100000 BTC | observed=0.00110000 BTC"));
    }

    @Test
    void duplicateLightningSettlementDoesNotCreditLedgerTwice() {
        ExternalTransferEntity transfer = lightningTransfer();
        when(walletCardProfileService.calculateDepositFee(42L, new BigDecimal("0.00100000")))
                .thenReturn(BigDecimal.ZERO);
        when(processedTransactionService.processOnce(eq("hash-duplicate"), eq("INBOUND_LIGHTNING"), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    Runnable processor = invocation.getArgument(2);
                    processor.run();
                    return true;
                })
                .thenReturn(false);

        assertTrue(service.settleLightningInbound(transfer, 100_000L, "hash-duplicate", "settled"));
        assertTrue(service.settleLightningInbound(transfer, 100_000L, "hash-duplicate", "settled again"));

        verify(ledgerPort, times(1)).updateBalance(
                7L,
                new BigDecimal("0.00100000"),
                "INBOUND_TRANSFER:" + transfer.getId());
        verify(ledgerPort, times(1)).recordHistory(any());
        verify(notificationPort, times(1)).notifyUser(eq(42L), any());
        assertEquals("COMPLETED", transfer.getStatus());
    }

    private ExternalTransferEntity lightningTransfer() {
        ExternalTransferEntity transfer = new ExternalTransferEntity();
        transfer.setId(UUID.randomUUID());
        transfer.setUserId(42L);
        transfer.setWalletId(7L);
        transfer.setWalletNameSnapshot("MAIN");
        transfer.setNetwork("LIGHTNING");
        transfer.setTransferType("INBOUND_INVOICE");
        transfer.setStatus("PENDING");
        transfer.setProvider("LND");
        transfer.setExpectedAmountBtc(new BigDecimal("0.00100000"));
        transfer.setAmountBtc(new BigDecimal("0.00100000"));
        return transfer;
    }
}
