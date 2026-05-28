package source.transactions.application.transaction;

import org.junit.jupiter.api.Test;
import source.transactions.dto.TransactionResponseDTO;
import source.transactions.model.PendingTransaction;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetTransactionStatusUseCaseTest {

    @Test
    void getStatusReturnsPendingTransactionFromStore() {
        TransactionPendingPort pendingPort = mock(TransactionPendingPort.class);
        PendingTransaction pendingTransaction = new PendingTransaction();
        pendingTransaction.setTxid("abc123");
        pendingTransaction.setStatus("PENDING");
        pendingTransaction.setFeeSatoshis(450L);
        pendingTransaction.setAmount(new BigDecimal("0.05000000"));
        when(pendingPort.findByTxid("abc123")).thenReturn(Optional.of(pendingTransaction));

        GetTransactionStatusUseCase useCase = new GetTransactionStatusUseCase(pendingPort);

        TransactionResponseDTO response = useCase.getStatus("abc123");

        assertEquals("abc123", response.getTxid());
        assertEquals("pending", response.getStatus());
        assertEquals(450L, response.getFeeSatoshis());
        assertEquals(new BigDecimal("0.05000000"), response.getAmountReceived());
    }

    @Test
    void getStatusReturnsConfirmedWhenTransactionIsNotTracked() {
        TransactionPendingPort pendingPort = mock(TransactionPendingPort.class);
        when(pendingPort.findByTxid("missing")).thenReturn(Optional.empty());

        GetTransactionStatusUseCase useCase = new GetTransactionStatusUseCase(pendingPort);

        TransactionResponseDTO response = useCase.getStatus("missing");

        assertEquals("confirmed", response.getStatus());
        assertEquals(0L, response.getFeeSatoshis());
    }
}
