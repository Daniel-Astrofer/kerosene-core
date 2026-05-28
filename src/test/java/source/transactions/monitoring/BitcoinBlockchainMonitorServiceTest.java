package source.transactions.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import source.transactions.infra.BlockchainClient;
import source.transactions.repository.ExternalTransferRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BitcoinBlockchainMonitorServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returnsBitcoinCoreSnapshotFromRealRpcShape() throws Exception {
        BlockchainClient client = mock(BlockchainClient.class);
        ExternalTransferRepository repository = mock(ExternalTransferRepository.class);
        when(repository.findTop200ByNetworkAndBlockchainTxidIsNotNullAndStatusInOrderByCreatedAtAsc(
                eq("BITCOIN"), anyCollection())).thenReturn(List.of());
        when(client.executeRpc("getblockchaininfo")).thenReturn(objectMapper.readTree("""
                {"result":{"chain":"main","blocks":100,"headers":100,"bestblockhash":"abc","difficulty":1.0,"verificationprogress":1.0,"initialblockdownload":false,"pruned":true,"pruneheight":50}}
                """));
        when(client.executeRpc("getmempoolinfo")).thenReturn(objectMapper.readTree("""
                {"result":{"size":12,"bytes":3456,"usage":4096,"mempoolminfee":0.00001}}
                """));
        when(client.executeRpc("getblock", "abc")).thenReturn(objectMapper.readTree("""
                {"result":{"time":1700000000,"nTx":7}}
                """));
        when(client.estimateSmartFee(2, 3, 6)).thenReturn(new BlockchainClient.FeeRates(20, 10, 5));

        BitcoinBlockchainMonitorService service = new BitcoinBlockchainMonitorService(
                provider(client), repository, "mainnet", true, true, "");

        var snapshot = service.snapshot();

        assertEquals("UP", snapshot.status());
        assertEquals("BITCOIN_PRUNED_NODE_RPC", snapshot.primarySource());
        assertEquals(100L, snapshot.chain().get("height"));
        assertEquals(true, snapshot.chain().get("pruned"));
        assertEquals(true, snapshot.chain().get("prunedRequired"));
        assertEquals(50L, snapshot.chain().get("pruneHeight"));
        assertEquals(12L, snapshot.mempool().get("transactions"));
    }

    @Test
    void reportsDegradedWhenPrunedNodeIsRequiredButRpcIsNotPruned() throws Exception {
        BlockchainClient client = mock(BlockchainClient.class);
        ExternalTransferRepository repository = mock(ExternalTransferRepository.class);
        when(repository.findTop200ByNetworkAndBlockchainTxidIsNotNullAndStatusInOrderByCreatedAtAsc(
                eq("BITCOIN"), anyCollection())).thenReturn(List.of());
        when(client.executeRpc("getblockchaininfo")).thenReturn(objectMapper.readTree("""
                {"result":{"chain":"main","blocks":100,"headers":100,"bestblockhash":"abc","difficulty":1.0,"verificationprogress":1.0,"initialblockdownload":false,"pruned":false}}
                """));
        when(client.executeRpc("getmempoolinfo")).thenReturn(objectMapper.readTree("""
                {"result":{"size":1,"bytes":100,"usage":200,"mempoolminfee":0.00001}}
                """));
        when(client.executeRpc("getblock", "abc")).thenReturn(objectMapper.readTree("""
                {"result":{"time":1700000000,"nTx":1}}
                """));
        when(client.estimateSmartFee(2, 3, 6)).thenReturn(new BlockchainClient.FeeRates(20, 10, 5));

        BitcoinBlockchainMonitorService service = new BitcoinBlockchainMonitorService(
                provider(client), repository, "mainnet", true, true, "");

        var snapshot = service.snapshot();

        assertEquals("DEGRADED", snapshot.status());
        assertEquals(false, snapshot.chain().get("pruned"));
        assertEquals("Bitcoin node is reachable but prune mode is required and not active", snapshot.message());
    }

    @Test
    void reportsDownWhenBlockchainProviderIsOffline() {
        BlockchainClient client = mock(BlockchainClient.class);
        ExternalTransferRepository repository = mock(ExternalTransferRepository.class);
        when(client.executeRpc("getblockchaininfo")).thenThrow(new IllegalStateException("offline"));

        BitcoinBlockchainMonitorService service = new BitcoinBlockchainMonitorService(
                provider(client), repository, "mainnet", true, true, "");

        assertEquals("DOWN", service.snapshot().status());
    }

    private ObjectProvider<BlockchainClient> provider(BlockchainClient client) {
        return new ObjectProvider<>() {
            @Override
            public BlockchainClient getObject(Object... args) {
                return client;
            }

            @Override
            public BlockchainClient getIfAvailable() {
                return client;
            }

            @Override
            public BlockchainClient getIfUnique() {
                return client;
            }

            @Override
            public BlockchainClient getObject() {
                return client;
            }
        };
    }
}
