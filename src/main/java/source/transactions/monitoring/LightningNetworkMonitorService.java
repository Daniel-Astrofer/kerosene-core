package source.transactions.monitoring;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import source.transactions.service.BitcoinNodeService;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class LightningNetworkMonitorService {

    private final ObjectProvider<BitcoinNodeService> bitcoinNodeServiceProvider;

    public LightningNetworkMonitorService(ObjectProvider<BitcoinNodeService> bitcoinNodeServiceProvider) {
        this.bitcoinNodeServiceProvider = bitcoinNodeServiceProvider;
    }

    public LightningMonitorSnapshot snapshot() {
        BitcoinNodeService node = bitcoinNodeServiceProvider.getIfAvailable();
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
            state.put("localBalanceSats", node.getLocalBalance());
            state.put("remoteBalanceSats", node.getRemoteBalance());
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

    public record LightningMonitorSnapshot(
            String status,
            String primarySource,
            Instant checkedAt,
            Map<String, Object> node,
            String message) {
    }
}
