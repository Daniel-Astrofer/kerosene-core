package source.transactions.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import source.common.infra.logging.LogSanitizer;
import source.transactions.infra.BlockchainClient;
import source.transactions.model.ExternalTransferEntity;
import source.transactions.repository.ExternalTransferRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BitcoinBlockchainMonitorService {

    private final ObjectProvider<BlockchainClient> blockchainClientProvider;
    private final ExternalTransferRepository externalTransferRepository;
    private final String network;
    private final boolean bitcoinCoreRequired;
    private final boolean prunedRequired;
    private final String indexerBaseUrl;

    public BitcoinBlockchainMonitorService(
            ObjectProvider<BlockchainClient> blockchainClientProvider,
            ExternalTransferRepository externalTransferRepository,
            @Value("${bitcoin.network:mainnet}") String network,
            @Value("${bitcoin.rpc.required:false}") boolean bitcoinCoreRequired,
            @Value("${bitcoin.rpc.pruned-required:false}") boolean prunedRequired,
            @Value("${bitcoin.indexer.base-url:${bitcoin.esplora.base-url:}}") String indexerBaseUrl) {
        this.blockchainClientProvider = blockchainClientProvider;
        this.externalTransferRepository = externalTransferRepository;
        this.network = network != null ? network : "mainnet";
        this.bitcoinCoreRequired = bitcoinCoreRequired;
        this.prunedRequired = prunedRequired;
        this.indexerBaseUrl = indexerBaseUrl != null ? indexerBaseUrl.trim() : "";
    }

    public BlockchainMonitorSnapshot snapshot() {
        BlockchainClient client = blockchainClientProvider.getIfAvailable();
        if (client == null) {
            return new BlockchainMonitorSnapshot(
                    "DOWN",
                    "NO_BLOCKCHAIN_CLIENT",
                    network,
                    "none",
                    false,
                    Instant.now(),
                    Map.of(),
                    Map.of(),
                    List.of(),
                    "No BlockchainClient bean is available");
        }

        try {
            JsonNode chain = unwrap(client.executeRpc("getblockchaininfo"));
            JsonNode mempool = unwrap(client.executeRpc("getmempoolinfo"));
            String bestHash = chain.path("bestblockhash").asText("");
            JsonNode bestBlock = bestHash.isBlank() ? null : unwrap(client.executeRpc("getblock", bestHash));
            BlockchainClient.FeeRates feeRates = client.estimateSmartFee(2, 3, 6);

            Map<String, Object> chainState = new LinkedHashMap<>();
            chainState.put("height", chain.path("blocks").asLong(0));
            chainState.put("headers", chain.path("headers").asLong(0));
            chainState.put("bestBlockHash", bestHash);
            chainState.put("chain", chain.path("chain").asText(network));
            chainState.put("difficulty", chain.path("difficulty").asDouble(0));
            chainState.put("verificationProgress", chain.path("verificationprogress").asDouble(0));
            chainState.put("initialBlockDownload", chain.path("initialblockdownload").asBoolean(false));
            boolean pruned = chain.path("pruned").asBoolean(false);
            chainState.put("pruned", pruned);
            chainState.put("prunedRequired", prunedRequired);
            if (chain.path("pruneheight").isNumber()) {
                chainState.put("pruneHeight", chain.path("pruneheight").asLong());
            }
            if (bestBlock != null && !bestBlock.isMissingNode()) {
                chainState.put("bestBlockTime", bestBlock.path("time").asLong(0));
                chainState.put("bestBlockTxCount", bestBlock.path("nTx").asLong(0));
            }

            Map<String, Object> mempoolState = new LinkedHashMap<>();
            mempoolState.put("transactions", mempool.path("size").asLong(0));
            mempoolState.put("bytes", mempool.path("bytes").asLong(0));
            mempoolState.put("usage", mempool.path("usage").asLong(0));
            mempoolState.put("minRelayFee", mempool.path("mempoolminfee").asDouble(0));
            mempoolState.put("feesSatPerVByte", Map.of(
                    "fast", feeRates.fastSatPerVByte(),
                    "halfHour", feeRates.halfHourSatPerVByte(),
                    "hour", feeRates.hourSatPerVByte()));

            List<Map<String, Object>> relevantTransactions = relevantTransactions();
            boolean synced = chain.path("blocks").asLong(0) > 0
                    && chain.path("headers").asLong(0) >= chain.path("blocks").asLong(0)
                    && !chain.path("initialblockdownload").asBoolean(false);
            boolean pruneSatisfied = !prunedRequired || pruned;
            String status = (synced && pruneSatisfied) || !bitcoinCoreRequired ? "UP" : "DEGRADED";

            return new BlockchainMonitorSnapshot(
                    status,
                    "BITCOIN_PRUNED_NODE_RPC",
                    network,
                    indexerBaseUrl.isBlank() ? "not-configured" : indexerBaseUrl,
                    !indexerBaseUrl.isBlank(),
                    Instant.now(),
                    chainState,
                    mempoolState,
                    relevantTransactions,
                    monitorMessage(synced, pruneSatisfied));
        } catch (Exception exception) {
            return new BlockchainMonitorSnapshot(
                    "DOWN",
                    "BITCOIN_PRUNED_NODE_RPC",
                    network,
                    indexerBaseUrl.isBlank() ? "not-configured" : indexerBaseUrl,
                    !indexerBaseUrl.isBlank(),
                    Instant.now(),
                    Map.of("exception", exception.getClass().getSimpleName()),
                    Map.of(),
                    List.of(),
                    "Bitcoin Core RPC probe failed");
        }
    }

    private String monitorMessage(boolean synced, boolean pruneSatisfied) {
        if (!pruneSatisfied) {
            return "Bitcoin node is reachable but prune mode is required and not active";
        }
        return synced
                ? "Bitcoin pruned node is synced"
                : "Bitcoin pruned node is reachable but not fully synced";
    }

    private List<Map<String, Object>> relevantTransactions() {
        return externalTransferRepository
                .findTop200ByNetworkAndBlockchainTxidIsNotNullAndStatusInOrderByCreatedAtAsc(
                        "BITCOIN",
                        List.of("PENDING", "PROCESSING", "DETECTED", "BROADCAST", "SETTLED", "COMPLETED"))
                .stream()
                .limit(25)
                .map(this::toRelevantTransaction)
                .toList();
    }

    private Map<String, Object> toRelevantTransaction(ExternalTransferEntity transfer) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", transfer.getId() != null ? transfer.getId().toString() : "");
        data.put("txidRef", LogSanitizer.fingerprint(transfer.getBlockchainTxid()));
        data.put("status", transfer.getStatus());
        data.put("confirmations", transfer.getConfirmations() != null ? transfer.getConfirmations() : 0);
        data.put("network", transfer.getNetwork());
        data.put("type", transfer.getTransferType());
        data.put("amountBtc", transfer.getAmountBtc());
        data.put("updatedAt", transfer.getUpdatedAt());
        return data;
    }

    private JsonNode unwrap(JsonNode response) {
        if (response != null && response.has("result")) {
            return response.get("result");
        }
        return response;
    }

    public record BlockchainMonitorSnapshot(
            String status,
            String primarySource,
            String network,
            String indexer,
            boolean localIndexerConfigured,
            Instant checkedAt,
            Map<String, Object> chain,
            Map<String, Object> mempool,
            List<Map<String, Object>> relevantTransactions,
            String message) {
    }
}
