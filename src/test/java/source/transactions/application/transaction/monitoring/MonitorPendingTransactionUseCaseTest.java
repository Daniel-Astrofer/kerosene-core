package source.transactions.application.transaction.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import source.transactions.application.transaction.TransactionPendingPort;
import source.transactions.application.transaction.monitoring.PendingTransactionBlockchainPort.BlockchainTransactionSnapshot;
import source.transactions.application.transaction.monitoring.handler.ConfirmationRegressionHandler;
import source.transactions.application.transaction.monitoring.handler.ConfirmedTransactionSettlementHandler;
import source.transactions.application.transaction.monitoring.handler.InvalidPendingTransactionHandler;
import source.transactions.application.transaction.monitoring.handler.LoadBlockchainSnapshotHandler;
import source.transactions.application.transaction.monitoring.handler.PendingDepositDetectedHandler;
import source.transactions.application.transaction.monitoring.handler.SynchronizeTransactionConfirmationsHandler;
import source.transactions.model.PendingTransaction;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MonitorPendingTransactionUseCaseTest {

    private PendingTransactionBlockchainPort blockchainPort;
    private PendingTransactionObservationPort observationPort;
    private PendingTransactionSettlementPort settlementPort;
    private TransactionPendingPort pendingPort;
    private MonitorPendingTransactionUseCase useCase;

    @BeforeEach
    void setUp() {
        blockchainPort = mock(PendingTransactionBlockchainPort.class);
        observationPort = mock(PendingTransactionObservationPort.class);
        settlementPort = mock(PendingTransactionSettlementPort.class);
        pendingPort = mock(TransactionPendingPort.class);

        PendingTransactionMonitorPipeline pipeline = new PendingTransactionMonitorPipeline(List.of(
                new InvalidPendingTransactionHandler(),
                new LoadBlockchainSnapshotHandler(blockchainPort),
                new PendingDepositDetectedHandler(observationPort),
                new SynchronizeTransactionConfirmationsHandler(observationPort),
                new ConfirmationRegressionHandler(3),
                new ConfirmedTransactionSettlementHandler(settlementPort, 3)));

        useCase = new MonitorPendingTransactionUseCase(pipeline, pendingPort);
    }

    @Test
    void checkMarksInvalidTransactionAsFailedAndPersistsIt() {
        PendingTransaction transaction = new PendingTransaction();
        transaction.setTxid("mock-123");

        useCase.check(transaction);

        assertEquals("FAILED", transaction.getStatus());
        assertEquals("Invalid TXID format - Cleanup", transaction.getErrorMessage());
        verify(pendingPort).save(transaction);
        verifyNoInteractions(blockchainPort, observationPort, settlementPort);
    }

    @Test
    void checkSkipsPersistenceWhenTransactionIsNotYetAvailableOnChain() {
        PendingTransaction transaction = pendingTransaction(validTxid());
        when(blockchainPort.loadTransaction(transaction.getTxid())).thenReturn(Optional.empty());

        useCase.check(transaction);

        verify(blockchainPort).loadTransaction(transaction.getTxid());
        verify(pendingPort, never()).save(any());
        verifyNoInteractions(observationPort, settlementPort);
    }

    @Test
    void checkConfirmsAndSettlesTransactionWhenMinimumDepthIsReached() {
        PendingTransaction transaction = pendingTransaction(validTxid());
        when(blockchainPort.loadTransaction(transaction.getTxid()))
                .thenReturn(Optional.of(new BlockchainTransactionSnapshot(4)));

        useCase.check(transaction);

        assertEquals("CONFIRMED", transaction.getStatus());
        assertEquals(4, transaction.getConfirmations());
        assertNotNull(transaction.getConfirmedAt());
        verify(observationPort).syncConfirmations(transaction.getTxid(), 4);
        verify(settlementPort).settleConfirmedTransaction(transaction, 4);
        verify(pendingPort).save(transaction);
    }

    @Test
    void checkDoesNotPersistConfirmedStatusWhenSettlementFails() {
        PendingTransaction transaction = pendingTransaction(validTxid());
        when(blockchainPort.loadTransaction(transaction.getTxid()))
                .thenReturn(Optional.of(new BlockchainTransactionSnapshot(4)));
        doThrow(new RuntimeException("boom"))
                .when(settlementPort)
                .settleConfirmedTransaction(transaction, 4);

        useCase.check(transaction);

        assertEquals("CONFIRMED", transaction.getStatus());
        verify(observationPort).syncConfirmations(transaction.getTxid(), 4);
        verify(settlementPort).settleConfirmedTransaction(transaction, 4);
        verify(pendingPort, never()).save(any());
    }

    @Test
    void checkReturnsConfirmedTransactionToPendingWhenConfirmationDepthDrops() {
        PendingTransaction transaction = pendingTransaction(validTxid());
        transaction.setStatus("CONFIRMED");
        transaction.setConfirmedAt(java.time.LocalDateTime.now().minusMinutes(5));
        when(blockchainPort.loadTransaction(transaction.getTxid()))
                .thenReturn(Optional.of(new BlockchainTransactionSnapshot(1)));

        useCase.check(transaction);

        assertEquals("PENDING", transaction.getStatus());
        assertEquals(1, transaction.getConfirmations());
        assertNull(transaction.getConfirmedAt());
        verify(observationPort).syncConfirmations(transaction.getTxid(), 1);
        verify(pendingPort).save(transaction);
        verifyNoInteractions(settlementPort);
    }

    private PendingTransaction pendingTransaction(String txid) {
        PendingTransaction transaction = new PendingTransaction();
        transaction.setTxid(txid);
        transaction.setAmount(new java.math.BigDecimal("0.25000000"));
        transaction.setUserId(42L);
        return transaction;
    }

    private String validTxid() {
        return "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";
    }
}
