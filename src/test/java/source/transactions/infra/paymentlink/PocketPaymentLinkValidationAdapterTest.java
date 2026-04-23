package source.transactions.infra.paymentlink;

import org.junit.jupiter.api.Test;
import source.transactions.application.paymentlink.PaymentLinkDescription;
import source.transactions.dto.PaymentLinkDTO;
import source.transactions.exception.PaymentLinkExceptions;
import source.transactions.infra.BlockchainClient;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PocketPaymentLinkValidationAdapterTest {

    @Test
    void shouldAcceptAnyNonBlankTxidForOnboardingVoucherWhenVoucherMockModeIsEnabled() {
        BlockchainClient blockchainClient = mock(BlockchainClient.class);
        PocketPaymentLinkValidationAdapter adapter = new PocketPaymentLinkValidationAdapter(
                blockchainClient,
                3,
                false,
                true);

        PaymentLinkDTO paymentLink = new PaymentLinkDTO();
        paymentLink.setDescription(PaymentLinkDescription.ONBOARDING_VOUCHER);

        assertDoesNotThrow(() -> adapter.validateConfirmedTransaction(
                paymentLink,
                "qualquer-txid-informado-pelo-cliente",
                "sender-address"));
        verifyNoInteractions(blockchainClient);
    }

    @Test
    void shouldAcceptAnyNonBlankTxidForAccountActivationWhenVoucherMockModeIsEnabled() {
        BlockchainClient blockchainClient = mock(BlockchainClient.class);
        PocketPaymentLinkValidationAdapter adapter = new PocketPaymentLinkValidationAdapter(
                blockchainClient,
                3,
                false,
                true);

        PaymentLinkDTO paymentLink = new PaymentLinkDTO();
        paymentLink.setDescription(PaymentLinkDescription.ACCOUNT_ACTIVATION);

        assertDoesNotThrow(() -> adapter.validateConfirmedTransaction(
                paymentLink,
                "qualquer-txid-informado-pelo-cliente",
                "sender-address"));
        verifyNoInteractions(blockchainClient);
    }

    @Test
    void shouldRejectBlankTxidEvenWhenVoucherMockModeIsEnabled() {
        BlockchainClient blockchainClient = mock(BlockchainClient.class);
        PocketPaymentLinkValidationAdapter adapter = new PocketPaymentLinkValidationAdapter(
                blockchainClient,
                3,
                false,
                true);

        PaymentLinkDTO paymentLink = new PaymentLinkDTO();
        paymentLink.setDescription(PaymentLinkDescription.ONBOARDING_VOUCHER);

        assertThrows(
                PaymentLinkExceptions.InvalidPaymentLinkTransaction.class,
                () -> adapter.validateConfirmedTransaction(paymentLink, " ", "sender-address"));
        verifyNoInteractions(blockchainClient);
    }

    @Test
    void shouldNotApplyVoucherMockModeToRegularPaymentLinks() {
        BlockchainClient blockchainClient = mock(BlockchainClient.class);
        PocketPaymentLinkValidationAdapter adapter = new PocketPaymentLinkValidationAdapter(
                blockchainClient,
                3,
                false,
                true);

        PaymentLinkDTO paymentLink = new PaymentLinkDTO();
        paymentLink.setDescription("regular");

        assertThrows(
                PaymentLinkExceptions.InvalidPaymentLinkTransaction.class,
                () -> adapter.validateConfirmedTransaction(paymentLink, "qualquer-txid", "sender-address"));
        verifyNoInteractions(blockchainClient);
    }
}
