package source.transactions.application.transaction;

import org.junit.jupiter.api.Test;
import source.transactions.model.PendingTransaction;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CheckPendingTransactionsUseCaseTest {

    @Test
    void checkAllDelegatesEachPendingTransactionToMonitor() {
        TransactionPendingPort pendingPort = mock(TransactionPendingPort.class);
        TransactionMonitorPort monitorPort = mock(TransactionMonitorPort.class);
        PendingTransaction first = new PendingTransaction();
        first.setTxid("tx-1");
        PendingTransaction second = new PendingTransaction();
        second.setTxid("tx-2");
        when(pendingPort.findPendingTransactions()).thenReturn(List.of(first, second));

        CheckPendingTransactionsUseCase useCase = new CheckPendingTransactionsUseCase(pendingPort, monitorPort);

        useCase.checkAll();

        verify(monitorPort).checkTransaction(first);
        verify(monitorPort).checkTransaction(second);
    }
}
