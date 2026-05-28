package source.transactions.infra.transaction;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.TestNet3Params;
import org.junit.jupiter.api.Test;
import source.common.service.AddressDerivationService;
import source.transactions.dto.TransactionRequestDTO;
import source.transactions.dto.UnsignedTransactionDTO;
import source.transactions.infra.BlockchainClient;

import java.math.BigDecimal;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BitcoinjUnsignedTransactionBuilderAdapterTest {

    @Test
    void buildsSerializedUnsignedTransactionFromConfirmedUtxos() {
        AddressDerivationService derivationService = new AddressDerivationService("testnet", "unsigned-tx-test");
        String fromAddress = derivationService.deriveAddress(1L, "source");
        String toAddress = derivationService.deriveAddress(2L, "destination");

        BlockchainClient blockchainClient = mock(BlockchainClient.class);
        when(blockchainClient.getUnspentOutputs(fromAddress)).thenReturn(List.of(
                new BlockchainClient.AddressUtxo(
                        "11".repeat(32),
                        1,
                        2_000_000L,
                        "0014script")));

        BitcoinjUnsignedTransactionBuilderAdapter adapter = new BitcoinjUnsignedTransactionBuilderAdapter(
                blockchainClient,
                "testnet");

        TransactionRequestDTO request = new TransactionRequestDTO();
        request.setFromAddress(fromAddress);
        request.setToAddress(toAddress);
        request.setAmount(new BigDecimal("0.01500000"));
        request.setFeeSatoshis(1_000L);

        UnsignedTransactionDTO unsignedTransaction = adapter.build(request);

        assertNotNull(unsignedTransaction.getTxId());
        assertFalse(unsignedTransaction.getRawTxHex().isBlank());
        assertEquals(fromAddress, unsignedTransaction.getFromAddress());
        assertEquals(toAddress, unsignedTransaction.getToAddress());
        assertEquals(new BigDecimal("0.01500000"), unsignedTransaction.getTotalAmount());
        assertEquals(1_000L, unsignedTransaction.getFee());
        assertEquals(1, unsignedTransaction.getInputs().size());
        assertEquals(2, unsignedTransaction.getOutputs().size());
        assertEquals(new BigDecimal("0.00499000"), unsignedTransaction.getOutputs().get(1).getValue());

        Transaction parsedTransaction = new Transaction(
                TestNet3Params.get(),
                HexFormat.of().parseHex(unsignedTransaction.getRawTxHex()));
        assertEquals(1, parsedTransaction.getInputs().size());
        assertEquals(2, parsedTransaction.getOutputs().size());
    }
}
