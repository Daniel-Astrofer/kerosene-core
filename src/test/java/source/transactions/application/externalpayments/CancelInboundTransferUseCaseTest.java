package source.transactions.application.externalpayments;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.transactions.exception.ExternalPaymentsExceptions;
import source.transactions.infra.CustodyGateway;
import source.transactions.model.ExternalTransferEntity;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelInboundTransferUseCaseTest {

    @Mock
    private ExternalTransfersPort externalTransfersPort;

    @Mock
    private CustodyGateway custodyGateway;

    @Test
    void cancelsPendingLightningInvoiceAndPersistsStatus() {
        ExternalTransferFactory factory = new ExternalTransferFactory(new ExternalPaymentsMath());
        CancelInboundTransferUseCase useCase = new CancelInboundTransferUseCase(
                externalTransfersPort,
                factory,
                custodyGateway);

        UUID transferId = UUID.randomUUID();
        ExternalTransferEntity transfer = new ExternalTransferEntity();
        transfer.setId(transferId);
        transfer.setUserId(7L);
        transfer.setWalletId(9L);
        transfer.setWalletNameSnapshot("MAIN");
        transfer.setTransferType("INBOUND_INVOICE");
        transfer.setStatus("PENDING");
        transfer.setExternalReference("payment-hash-123");
        transfer.setInvoiceData("lnbc1example");

        when(externalTransfersPort.findByIdAndUserId(transferId, 7L)).thenReturn(Optional.of(transfer));
        when(custodyGateway.cancelLightningInvoice(any())).thenReturn(true);
        when(externalTransfersPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = useCase.cancel(7L, transferId);

        assertEquals("CANCELLED", response.status());
        ArgumentCaptor<CustodyGateway.LightningInvoiceCancellationCommand> captor = ArgumentCaptor.forClass(
                CustodyGateway.LightningInvoiceCancellationCommand.class);
        verify(custodyGateway).cancelLightningInvoice(captor.capture());
        assertEquals("payment-hash-123", captor.getValue().paymentHash());
    }

    @Test
    void rejectsCancellationForCompletedTransfer() {
        ExternalTransferFactory factory = new ExternalTransferFactory(new ExternalPaymentsMath());
        CancelInboundTransferUseCase useCase = new CancelInboundTransferUseCase(
                externalTransfersPort,
                factory,
                custodyGateway);

        UUID transferId = UUID.randomUUID();
        ExternalTransferEntity transfer = new ExternalTransferEntity();
        transfer.setId(transferId);
        transfer.setUserId(7L);
        transfer.setTransferType("ONRAMP_PURCHASE");
        transfer.setStatus("COMPLETED");

        when(externalTransfersPort.findByIdAndUserId(transferId, 7L)).thenReturn(Optional.of(transfer));

        assertThrows(
                ExternalPaymentsExceptions.TransferCancellationNotAllowed.class,
                () -> useCase.cancel(7L, transferId));
    }
}
