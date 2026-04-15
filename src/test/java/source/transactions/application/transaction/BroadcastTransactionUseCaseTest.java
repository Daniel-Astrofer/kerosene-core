package source.transactions.application.transaction;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import source.transactions.dto.TransactionResponseDTO;
import source.transactions.exception.TransactionExceptions;
import source.transactions.model.PendingTransaction;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BroadcastTransactionUseCaseTest {

    @Test
    void broadcastPersistsPendingTransactionAndSendsNotifications() {
        TransactionBroadcastPort broadcastPort = mock(TransactionBroadcastPort.class);
        TransactionPendingPort pendingPort = mock(TransactionPendingPort.class);
        TransactionHistoryPort historyPort = mock(TransactionHistoryPort.class);
        TransactionNotificationPort notificationPort = mock(TransactionNotificationPort.class);
        when(broadcastPort.sendRawTransaction("signed-hex")).thenReturn("txid-123");

        BroadcastTransactionUseCase useCase = new BroadcastTransactionUseCase(
                broadcastPort,
                pendingPort,
                historyPort,
                notificationPort);

        TransactionResponseDTO response = useCase.broadcast(
                "signed-hex",
                "bc1recipient",
                new BigDecimal("0.25000000"),
                "payout",
                42L);

        ArgumentCaptor<PendingTransaction> pendingCaptor = ArgumentCaptor.forClass(PendingTransaction.class);
        verify(pendingPort).save(pendingCaptor.capture());
        assertEquals("txid-123", pendingCaptor.getValue().getTxid());
        assertEquals("bc1recipient", pendingCaptor.getValue().getToAddress());
        assertEquals(new BigDecimal("0.25000000"), pendingCaptor.getValue().getAmount());

        ArgumentCaptor<TransactionHistoryPort.BroadcastRecord> historyCaptor =
                ArgumentCaptor.forClass(TransactionHistoryPort.BroadcastRecord.class);
        verify(historyPort).recordBroadcast(historyCaptor.capture());
        assertEquals("txid-123", historyCaptor.getValue().txid());
        assertEquals(42L, historyCaptor.getValue().userId());
        assertEquals("bc1recipient", historyCaptor.getValue().toAddress());
        assertEquals("payout", historyCaptor.getValue().message());

        verify(notificationPort).notifySenderBroadcast(42L, new BigDecimal("0.25000000"));
        verify(notificationPort).notifyRecipientBroadcast("bc1recipient", new BigDecimal("0.25000000"), "payout");
        assertEquals("txid-123", response.getTxid());
        assertEquals("pending", response.getStatus());
        assertEquals(new BigDecimal("0.25000000"), response.getAmountReceived());
    }

    @Test
    void broadcastFailsWhenGatewayDoesNotReturnTxid() {
        TransactionBroadcastPort broadcastPort = mock(TransactionBroadcastPort.class);
        when(broadcastPort.sendRawTransaction("signed-hex")).thenReturn(" ");

        BroadcastTransactionUseCase useCase = new BroadcastTransactionUseCase(
                broadcastPort,
                mock(TransactionPendingPort.class),
                mock(TransactionHistoryPort.class),
                mock(TransactionNotificationPort.class));

        assertThrows(TransactionExceptions.TransactionBroadcastFailed.class,
                () -> useCase.broadcast("signed-hex", "bc1recipient", BigDecimal.ONE, null, 1L));
    }

    @Test
    void broadcastDoesNotFailWhenNotificationsOrHistoryFail() {
        TransactionBroadcastPort broadcastPort = mock(TransactionBroadcastPort.class);
        TransactionPendingPort pendingPort = mock(TransactionPendingPort.class);
        TransactionHistoryPort historyPort = mock(TransactionHistoryPort.class);
        TransactionNotificationPort notificationPort = mock(TransactionNotificationPort.class);
        when(broadcastPort.sendRawTransaction("signed-hex")).thenReturn("txid-456");
        doThrow(new RuntimeException("history down")).when(historyPort).recordBroadcast(any());
        doThrow(new RuntimeException("notifications down")).when(notificationPort).notifySenderBroadcast(any(), any());

        BroadcastTransactionUseCase useCase = new BroadcastTransactionUseCase(
                broadcastPort,
                pendingPort,
                historyPort,
                notificationPort);

        TransactionResponseDTO response = useCase.broadcast(
                "signed-hex",
                null,
                new BigDecimal("0.01000000"),
                null,
                99L);

        verify(pendingPort).save(any(PendingTransaction.class));
        verify(notificationPort).notifySenderBroadcast(99L, new BigDecimal("0.01000000"));
        verify(notificationPort, never()).notifyRecipientBroadcast(any(), any(), any());
        assertEquals("txid-456", response.getTxid());
    }
}
