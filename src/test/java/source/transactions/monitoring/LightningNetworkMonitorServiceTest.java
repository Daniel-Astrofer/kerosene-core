package source.transactions.monitoring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import source.transactions.infra.lnd.proto.lnrpc.GetInfoResponse;
import source.transactions.service.BitcoinNodeService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LightningNetworkMonitorServiceTest {

    @Test
    void reportsLndSnapshotFromGrpcNode() {
        BitcoinNodeService node = mock(BitcoinNodeService.class);
        when(node.isLive()).thenReturn(true);
        when(node.getInfo()).thenReturn(GetInfoResponse.newBuilder()
                .setIdentityPubkey("02abcdef")
                .setAlias("kerosene-mainnet")
                .setVersion("0.20.1-beta")
                .setSyncedToChain(true)
                .setSyncedToGraph(true)
                .setBlockHeight(840000)
                .setBlockHash("00000000000000000000abc")
                .setNumPeers(3)
                .setNumActiveChannels(2)
                .setNumInactiveChannels(1)
                .setNumPendingChannels(0)
                .build());
        when(node.getLocalBalance()).thenReturn(10_000L);
        when(node.getRemoteBalance()).thenReturn(20_000L);
        when(node.getHotWalletBalance()).thenReturn(30_000L);

        LightningNetworkMonitorService service = new LightningNetworkMonitorService(provider(node));

        var snapshot = service.snapshot();

        assertEquals("UP", snapshot.status());
        assertEquals("LND_GRPC", snapshot.primarySource());
        assertEquals(840000, snapshot.node().get("blockHeight"));
        assertEquals(10_000L, snapshot.node().get("localBalanceSats"));
    }

    @Test
    void reportsDownWhenLndIsUnavailable() {
        LightningNetworkMonitorService service = new LightningNetworkMonitorService(provider(null));

        var snapshot = service.snapshot();

        assertEquals("DOWN", snapshot.status());
        assertEquals("LND_GRPC", snapshot.primarySource());
    }

    private ObjectProvider<BitcoinNodeService> provider(BitcoinNodeService node) {
        return new ObjectProvider<>() {
            @Override
            public BitcoinNodeService getObject(Object... args) {
                return node;
            }

            @Override
            public BitcoinNodeService getIfAvailable() {
                return node;
            }

            @Override
            public BitcoinNodeService getIfUnique() {
                return node;
            }

            @Override
            public BitcoinNodeService getObject() {
                return node;
            }
        };
    }
}
