package source.bitcoinaccounts.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import source.bitcoinaccounts.model.BitcoinAccountEnums;
import source.bitcoinaccounts.model.ReceivingAddressEntity;
import source.bitcoinaccounts.repository.ReceivingAddressRepository;
import source.transactions.infra.BlockchainClient;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BitcoinReceivingMonitorServiceTest {

    @Test
    void scansAddressTransactionsAndObservesMatchingOutput() {
        ReceivingAddressRepository addressRepository = mock(ReceivingAddressRepository.class);
        ReceivingRequestService receivingRequestService = mock(ReceivingRequestService.class);
        BlockchainClient blockchainClient = mock(BlockchainClient.class);
        BitcoinReceivingMonitorService service = new BitcoinReceivingMonitorService(
                addressRepository,
                receivingRequestService,
                blockchainClient);

        ReceivingAddressEntity address = new ReceivingAddressEntity();
        address.setCardId(java.util.UUID.randomUUID());
        address.setAddress("bcrt1qreceivetest0000000000000000000");
        address.setDerivationPath("m/84'/1'/0'/0/7");
        address.setDerivationIndex(7);
        address.setStatus(BitcoinAccountEnums.ReceivingAddressStatus.ASSIGNED);

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode transactions = mapper.createArrayNode();
        ObjectNode transaction = transactions.addObject();
        transaction.put("txid", "tx456");
        transaction.put("confirmations", 1);
        ObjectNode output = transaction.putArray("vout").addObject();
        output.put("value", 25_000L);
        output.put("scriptpubkey_address", address.getAddress());

        when(addressRepository.findTop200ByStatusInOrderByUpdatedAtAsc(anyList())).thenReturn(List.of(address));
        when(blockchainClient.getAddressTransactions(address.getAddress())).thenReturn(transactions);

        service.scanReceivingAddresses();

        verify(receivingRequestService).observeOnchainPayment(
                address.getAddress(),
                "tx456",
                0,
                25_000L,
                1);
    }
}
