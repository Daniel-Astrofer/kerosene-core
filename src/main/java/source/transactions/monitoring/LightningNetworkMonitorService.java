package source.transactions.monitoring;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import source.transactions.service.LndLightningNodeClient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class LightningNetworkMonitorService {

    private final ObjectProvider<LndLightningNodeClient> bitcoinNodeServiceProvider;

    public LightningNetworkMonitorService(ObjectProvider<LndLightningNodeClient> bitcoinNodeServiceProvider) {
        this.bitcoinNodeServiceProvider = bitcoinNodeServiceProvider;
    }

    public LightningMonitorSnapshot snapshot() {
        LndLightningNodeClient node = bitcoinNodeServiceProvider.getIfAvailable();
        if (node == null || !node.isLive()) {
            return new LightningMonitorSnapshot(
                    "DOWN",
                    "LND_GRPC",
                    Instant.now(),
                    Map.of(),
                    "LND gRPC is not configured or not live");
        }

        try {
            var info = node.getInfo();
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("identityPubkey", info.getIdentityPubkey());
            state.put("alias", info.getAlias());
            state.put("version", info.getVersion());
            state.put("syncedToChain", info.getSyncedToChain());
            state.put("syncedToGraph", info.getSyncedToGraph());
            state.put("blockHeight", info.getBlockHeight());
            state.put("blockHash", info.getBlockHash());
            state.put("numPeers", info.getNumPeers());
            state.put("numActiveChannels", info.getNumActiveChannels());
            state.put("numInactiveChannels", info.getNumInactiveChannels());
            state.put("numPendingChannels", info.getNumPendingChannels());
            long localBalanceSats = node.getLocalBalance();
            long remoteBalanceSats = node.getRemoteBalance();
            long channelLiquiditySats = Math.max(0L, localBalanceSats + remoteBalanceSats);
            state.put("localBalanceSats", localBalanceSats);
            state.put("remoteBalanceSats", remoteBalanceSats);
            state.put("outboundLiquidityRatio", ratio(localBalanceSats, channelLiquiditySats));
            state.put("inboundLiquidityRatio", ratio(remoteBalanceSats, channelLiquiditySats));
            state.put("channelLiquiditySats", channelLiquiditySats);
            state.put("walletConfirmedBalanceSats", node.getHotWalletBalance());

            boolean synced = info.getSyncedToChain();
            return new LightningMonitorSnapshot(
                    synced ? "UP" : "DEGRADED",
                    "LND_GRPC",
                    Instant.now(),
                    state,
                    synced ? "LND is synced to chain" : "LND is reachable but not synced to chain");
        } catch (Exception exception) {
            return new LightningMonitorSnapshot(
                    "DOWN",
                    "LND_GRPC",
                    Instant.now(),
                    Map.of("exception", exception.getClass().getSimpleName()),
                    "LND gRPC probe failed");
        }
    }

    private double ratio(long value, long total) {
        if (total <= 0L || value <= 0L) {
            return 0.0d;
        }
        return Math.min(1.0d, (double) value / (double) total);
    }

    public record LightningMonitorSnapshot(
            String status,
            String primarySource,
            Instant checkedAt,
            Map<String, Object> node,
            String message) {
    }
}
