package source.transactions.infra.paymentlink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import source.transactions.dto.PaymentLinkDTO;
import source.transactions.exception.PaymentLinkExceptions;
import source.transactions.infra.BlockchainClient;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PocketPaymentLinkValidationAdapterTest {

    private static final String TXID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String EXPECTED_ADDRESS = "bc1qexpectedpaymentlinkaddress";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRejectTxidThatDoesNotExistOnBlockchain() {
        BlockchainClient blockchainClient = mock(BlockchainClient.class);
        when(blockchainClient.getRawTransaction(TXID, true)).thenReturn(null);
        PocketPaymentLinkValidationAdapter adapter = adapter(blockchainClient, 3);

        assertThrows(
                PaymentLinkExceptions.InvalidPaymentLinkTransaction.class,
                () -> adapter.validateConfirmedTransaction(paymentLink("0.00100000"), TXID, "sender-address"));
    }

    @Test
    void shouldRejectTransactionPaidToWrongAddress() throws Exception {
        BlockchainClient blockchainClient = mock(BlockchainClient.class);
        when(blockchainClient.getRawTransaction(TXID, true))
                .thenReturn(tx(3, "bc1qwrongaddress", 100000L));
        PocketPaymentLinkValidationAdapter adapter = adapter(blockchainClient, 3);

        assertThrows(
                PaymentLinkExceptions.InvalidPaymentLinkTransaction.class,
                () -> adapter.validateConfirmedTransaction(paymentLink("0.00100000"), TXID, "sender-address"));
    }

    @Test
    void shouldRejectTransactionWithLowerThanExpectedValue() throws Exception {
        BlockchainClient blockchainClient = mock(BlockchainClient.class);
        when(blockchainClient.getRawTransaction(TXID, true))
                .thenReturn(tx(3, EXPECTED_ADDRESS, 99999L));
        PocketPaymentLinkValidationAdapter adapter = adapter(blockchainClient, 3);

        assertThrows(
                PaymentLinkExceptions.InvalidPaymentLinkTransaction.class,
                () -> adapter.validateConfirmedTransaction(paymentLink("0.00100000"), TXID, "sender-address"));
    }

    @Test
    void shouldRejectTransactionWithInsufficientConfirmations() throws Exception {
        BlockchainClient blockchainClient = mock(BlockchainClient.class);
        when(blockchainClient.getRawTransaction(TXID, true))
                .thenReturn(tx(2, EXPECTED_ADDRESS, 100000L));
        PocketPaymentLinkValidationAdapter adapter = adapter(blockchainClient, 3);

        assertThrows(
                PaymentLinkExceptions.InvalidPaymentLinkTransaction.class,
                () -> adapter.validateConfirmedTransaction(paymentLink("0.00100000"), TXID, "sender-address"));
    }

    @Test
    void shouldAcceptConfirmedTransactionPayingExpectedAddressAndAmount() throws Exception {
        BlockchainClient blockchainClient = mock(BlockchainClient.class);
        when(blockchainClient.getRawTransaction(TXID, true))
                .thenReturn(tx(3, EXPECTED_ADDRESS, 100000L));
        PocketPaymentLinkValidationAdapter adapter = adapter(blockchainClient, 3);

        assertDoesNotThrow(
                () -> adapter.validateConfirmedTransaction(paymentLink("0.00100000"), TXID, "sender-address"));
    }

    @Test
    void shouldRejectMockTxidEvenWhenLegacyMockFlagsAreEnabled() {
        BlockchainClient blockchainClient = mock(BlockchainClient.class);
        PocketPaymentLinkValidationAdapter adapter = new PocketPaymentLinkValidationAdapter(
                blockchainClient,
                3,
                true,
                true);

        assertThrows(
                PaymentLinkExceptions.InvalidPaymentLinkTransaction.class,
                () -> adapter.validateConfirmedTransaction(paymentLink("0.00100000"), "mock-txid", "sender-address"));
    }

    private PocketPaymentLinkValidationAdapter adapter(BlockchainClient blockchainClient, int confirmations) {
        return new PocketPaymentLinkValidationAdapter(blockchainClient, confirmations, false, false);
    }

    private PaymentLinkDTO paymentLink(String amountBtc) {
        PaymentLinkDTO paymentLink = new PaymentLinkDTO();
        paymentLink.setId("pay-1");
        paymentLink.setAmountBtc(new BigDecimal(amountBtc));
        paymentLink.setAmountLocked(true);
        paymentLink.setDepositAddress(EXPECTED_ADDRESS);
        return paymentLink;
    }

    private JsonNode tx(int confirmations, String address, long valueSats) throws Exception {
        return objectMapper.readTree("""
                {
                  "confirmations": %d,
                  "vout": [
                    {
                      "scriptpubkey_address": "%s",
                      "value": %d
                    }
                  ]
                }
                """.formatted(confirmations, address, valueSats));
    }
}
