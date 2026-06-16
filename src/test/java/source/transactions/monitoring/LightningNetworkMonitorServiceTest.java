package source.transactions.monitoring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import source.transactions.infra.lnd.proto.lnrpc.GetInfoResponse;
import source.transactions.service.LndLightningNodeClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LightningNetworkMonitorServiceTest {

    @Test
    void reportsLndSnapshotFromGrpcNode() {
        LndLightningNodeClient node = mock(LndLightningNodeClient.class);
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
        assertEquals(30_000L, snapshot.node().get("channelLiquiditySats"));
        assertEquals(1.0d / 3.0d, (double) snapshot.node().get("outboundLiquidityRatio"), 0.000001d);
        assertEquals(2.0d / 3.0d, (double) snapshot.node().get("inboundLiquidityRatio"), 0.000001d);
    }

    @Test
    void reportsDownWhenLndIsUnavailable() {
        LightningNetworkMonitorService service = new LightningNetworkMonitorService(provider(null));

        var snapshot = service.snapshot();

        assertEquals("DOWN", snapshot.status());
        assertEquals("LND_GRPC", snapshot.primarySource());
    }

    private ObjectProvider<LndLightningNodeClient> provider(LndLightningNodeClient node) {
        return new ObjectProvider<>() {
            @Override
            public LndLightningNodeClient getObject(Object... args) {
                return node;
            }

            @Override
            public LndLightningNodeClient getIfAvailable() {
                return node;
            }

            @Override
            public LndLightningNodeClient getIfUnique() {
                return node;
            }

            @Override
            public LndLightningNodeClient getObject() {
                return node;
            }
        };
    }
}
