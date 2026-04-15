package source.transactions.application.transaction;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import source.transactions.dto.TransactionRequestDTO;
import source.transactions.dto.UnsignedTransactionDTO;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CreateUnsignedTransactionUseCaseTest {

    @Test
    void createBuildsPlaceholderTransactionAndRecordsHistory() {
        TransactionHistoryPort historyPort = mock(TransactionHistoryPort.class);
        CreateUnsignedTransactionUseCase useCase = new CreateUnsignedTransactionUseCase(historyPort);

        TransactionRequestDTO request = new TransactionRequestDTO();
        request.setFromAddress("bc1fromaddress");
        request.setToAddress("bc1toaddress");
        request.setAmount(new BigDecimal("0.01500000"));
        request.setFeeSatoshis(1234L);

        UnsignedTransactionDTO unsignedTransaction = useCase.create(request);

        assertNotNull(unsignedTransaction.getTxId());
        assertEquals("bc1fromaddress", unsignedTransaction.getFromAddress());
        assertEquals("bc1toaddress", unsignedTransaction.getToAddress());
        assertEquals(new BigDecimal("0.01500000"), unsignedTransaction.getTotalAmount());
        assertEquals(1234L, unsignedTransaction.getFee());
        assertEquals(CreateUnsignedTransactionUseCase.RAW_TX_HEX_PLACEHOLDER, unsignedTransaction.getRawTxHex());

        ArgumentCaptor<TransactionHistoryPort.UnsignedTransactionRecord> captor =
                ArgumentCaptor.forClass(TransactionHistoryPort.UnsignedTransactionRecord.class);
        verify(historyPort).recordUnsignedTransaction(captor.capture());
        assertEquals("bc1fromaddress", captor.getValue().fromAddress());
        assertEquals("bc1toaddress", captor.getValue().toAddress());
        assertEquals(new BigDecimal("0.01500000"), captor.getValue().amount());
    }
}
