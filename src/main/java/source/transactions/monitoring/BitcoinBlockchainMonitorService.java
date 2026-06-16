package source.transactions.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import source.common.infra.logging.LogSanitizer;
import source.transactions.infra.BitcoinCoreRpcClient;
import source.transactions.infra.BlockchainClient;
import source.transactions.model.ExternalTransferEntity;
import source.transactions.repository.ExternalTransferRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BitcoinBlockchainMonitorService {

    private static final Logger log = LoggerFactory.getLogger(BitcoinBlockchainMonitorService.class);

    private final ObjectProvider<BlockchainClient> blockchainClientProvider;
    private final ExternalTransferRepository externalTransferRepository;
    private final String network;
    private final boolean bitcoinCoreRequired;
    private final boolean prunedRequired;
    private final String indexerBaseUrl;
    private final boolean autoSyncEnabled;
    private final double minimumVerificationProgress;
    private final Duration syncTriggerCooldown;
    private final Object syncTriggerLock = new Object();
    private volatile Instant lastSyncTriggerAt = Instant.EPOCH;
    private volatile Map<String, Object> lastSyncTrigger = Map.of("status", "NOT_TRIGGERED");

    public BitcoinBlockchainMonitorService(
            ObjectProvider<BlockchainClient> blockchainClientProvider,
            ExternalTransferRepository externalTransferRepository,
            @Value("${bitcoin.network:mainnet}") String network,
            @Value("${bitcoin.rpc.required:false}") boolean bitcoinCoreRequired,
            @Value("${bitcoin.rpc.pruned-required:false}") boolean prunedRequired,
            @Value("${bitcoin.indexer.base-url:${bitcoin.esplora.base-url:}}") String indexerBaseUrl,
            @Value("${bitcoin.rpc.auto-sync.enabled:true}") boolean autoSyncEnabled,
            @Value("${bitcoin.rpc.auto-sync.minimum-verification-progress:0.999}") double minimumVerificationProgress,
            @Value("${bitcoin.rpc.auto-sync.cooldown-ms:300000}") long syncTriggerCooldownMs) {
        this.blockchainClientProvider = blockchainClientProvider;
        this.externalTransferRepository = externalTransferRepository;
        this.network = network != null ? network : "mainnet";
        this.bitcoinCoreRequired = bitcoinCoreRequired;
        this.prunedRequired = prunedRequired;
        this.indexerBaseUrl = indexerBaseUrl != null ? indexerBaseUrl.trim() : "";
        this.autoSyncEnabled = autoSyncEnabled;
        this.minimumVerificationProgress = Math.max(0.0d, Math.min(1.0d, minimumVerificationProgress));
        this.syncTriggerCooldown = Duration.ofMillis(Math.max(10_000L, syncTriggerCooldownMs));
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
            JsonNode chain = nodeRpc(client, "getblockchaininfo");
            JsonNode mempool = nodeRpc(client, "getmempoolinfo");
            JsonNode networkInfo = safeNodeRpc(client, "getnetworkinfo");
            JsonNode walletInfo = safeRpc(client, "getwalletinfo");
            String bestHash = chain.path("bestblockhash").asText("");
            JsonNode bestBlock = bestHash.isBlank() ? null : safeNodeRpc(client, "getblock", bestHash);
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
            chainState.put("sizeOnDiskBytes", chain.path("size_on_disk").asLong(0L));
            chainState.put("automaticPruning", chain.path("automatic_pruning").asBoolean(false));
            chainState.put("pruneTargetSizeBytes", chain.path("prune_target_size").asLong(0L));
            if (chain.path("pruneheight").isNumber()) {
                chainState.put("pruneHeight", chain.path("pruneheight").asLong());
            }
            if (bestBlock != null && !bestBlock.isMissingNode()) {
                chainState.put("bestBlockTime", bestBlock.path("time").asLong(0));
                chainState.put("bestBlockTxCount", bestBlock.path("nTx").asLong(0));
            }
            if (networkInfo != null && !networkInfo.isMissingNode()) {
                chainState.put("nodeVersion", networkInfo.path("version").asLong(0L));
                chainState.put("subversion", networkInfo.path("subversion").asText(""));
                chainState.put("connections", networkInfo.path("connections").asLong(0L));
            }
            if (walletInfo != null && !walletInfo.isMissingNode()) {
                chainState.put("walletName", walletInfo.path("walletname").asText(""));
                chainState.put("walletScanning", walletScanningState(walletInfo.path("scanning")));
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

            chainState.put("syncTrigger", maybeTriggerSync(client, chain, walletInfo, false));
            List<Map<String, Object>> relevantTransactions = relevantTransactions();
            boolean synced = chain.path("blocks").asLong(0) > 0
                    && chain.path("blocks").asLong(0) >= chain.path("headers").asLong(0)
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

    @Scheduled(
            fixedDelayString = "${bitcoin.rpc.auto-sync.fixed-delay-ms:60000}",
            initialDelayString = "${bitcoin.rpc.auto-sync.initial-delay-ms:15000}")
    public void monitorPrunedNodeSync() {
        if (autoSyncEnabled) {
            try {
                snapshot();
            } catch (RuntimeException exception) {
                log.warn("[BitcoinMonitor] Scheduled pruned-node probe failed: {}", exception.getMessage());
            }
        }
    }

    public Map<String, Object> triggerSyncSearch() {
        BlockchainClient client = blockchainClientProvider.getIfAvailable();
        if (client == null) {
            return Map.of("status", "SKIPPED", "reason", "NO_BLOCKCHAIN_CLIENT");
        }
        try {
            JsonNode chain = nodeRpc(client, "getblockchaininfo");
            JsonNode walletInfo = safeRpc(client, "getwalletinfo");
            return maybeTriggerSync(client, chain, walletInfo, true);
        } catch (Exception exception) {
            Map<String, Object> failed = new LinkedHashMap<>();
            failed.put("status", "FAILED");
            failed.put("reason", "BITCOIN_CORE_RPC_PROBE_FAILED");
            failed.put("exception", exception.getClass().getSimpleName());
            lastSyncTrigger = Map.copyOf(failed);
            return failed;
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
        try {
            return externalTransferRepository
                    .findTop200ByNetworkAndBlockchainTxidIsNotNullAndStatusInOrderByCreatedAtAsc(
                            "BITCOIN",
                            List.of("PENDING", "PROCESSING", "DETECTED", "BROADCAST", "SETTLED", "COMPLETED"))
                    .stream()
                    .limit(25)
                    .map(this::toRelevantTransaction)
                    .toList();
        } catch (RuntimeException exception) {
            log.warn("[BitcoinMonitor] Failed to load relevant transfer snapshot: {}", exception.getMessage());
            return List.of();
        }
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

    private Map<String, Object> maybeTriggerSync(
            BlockchainClient client,
            JsonNode chain,
            JsonNode walletInfo,
            boolean force) {
        synchronized (syncTriggerLock) {
            return maybeTriggerSyncLocked(client, chain, walletInfo, force);
        }
    }

    private Map<String, Object> maybeTriggerSyncLocked(
            BlockchainClient client,
            JsonNode chain,
            JsonNode walletInfo,
            boolean force) {
        if (!force && !autoSyncEnabled) {
            return mergeTriggerState("DISABLED", "AUTO_SYNC_DISABLED");
        }
        if (chain == null || chain.isMissingNode() || chain.isNull()) {
            return mergeTriggerState("SKIPPED", "NO_CHAIN_STATE");
        }
        if (!force && !syncLooksLagged(chain, walletInfo) && !configuredCoreWalletUnavailable(client, walletInfo)) {
            return mergeTriggerState("NOT_NEEDED", "CHAIN_AND_WALLET_SCAN_CURRENT");
        }
        Instant now = Instant.now();
        if (!force && Duration.between(lastSyncTriggerAt, now).compareTo(syncTriggerCooldown) < 0) {
            return mergeTriggerState("THROTTLED", "COOLDOWN_ACTIVE");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        long startHeight = rescanStartHeight(chain);
        result.put("requestedAt", now);
        result.put("startHeight", startHeight);
        result.put("forced", force);
        try {
            if (client instanceof BitcoinCoreRpcClient bitcoinCore) {
                result.put("walletLoaded", bitcoinCore.loadConfiguredWallet());
                BitcoinCoreRpcClient.RescanResult rescan = bitcoinCore.rescanBlockchain(startHeight);
                result.put("rescanStartHeight", rescan.startHeight());
                result.put("rescanStopHeight", rescan.stopHeight());
            } else {
                unwrap(client.executeRpc("rescanblockchain", startHeight));
                result.put("walletLoaded", false);
            }
            result.put("status", "TRIGGERED");
            result.put("reason", force ? "MANUAL_TRIGGER" : "CHAIN_OR_WALLET_SCAN_LAGGED");
        } catch (Exception exception) {
            result.put("status", "FAILED");
            result.put("reason", exception.getMessage() != null ? exception.getMessage() : "SYNC_TRIGGER_FAILED");
            result.put("exception", exception.getClass().getSimpleName());
        }

        lastSyncTriggerAt = now;
        lastSyncTrigger = Map.copyOf(result);
        return result;
    }

    private boolean syncLooksLagged(JsonNode chain, JsonNode walletInfo) {
        long blocks = chain.path("blocks").asLong(0L);
        long headers = chain.path("headers").asLong(0L);
        double verificationProgress = chain.path("verificationprogress").asDouble(0.0d);
        boolean chainLagged = chain.path("initialblockdownload").asBoolean(false)
                || blocks <= 0L
                || headers > blocks
                || verificationProgress < minimumVerificationProgress;
        return chainLagged || walletScanningInProgress(walletInfo);
    }

    private boolean configuredCoreWalletUnavailable(BlockchainClient client, JsonNode walletInfo) {
        return client instanceof BitcoinCoreRpcClient bitcoinCore
                && bitcoinCore.walletName() != null
                && !bitcoinCore.walletName().isBlank()
                && (walletInfo == null || walletInfo.isMissingNode() || walletInfo.isNull());
    }

    private boolean walletScanningInProgress(JsonNode walletInfo) {
        if (walletInfo == null || walletInfo.isMissingNode() || walletInfo.isNull()) {
            return false;
        }
        JsonNode scanning = walletInfo.path("scanning");
        if (scanning.isObject()) {
            return scanning.path("progress").asDouble(1.0d) < 1.0d;
        }
        return scanning.asBoolean(false);
    }

    private long rescanStartHeight(JsonNode chain) {
        long blocks = Math.max(0L, chain.path("blocks").asLong(0L));
        long pruneHeight = Math.max(0L, chain.path("pruneheight").asLong(0L));
        long boundedPruneHeight = Math.min(pruneHeight, blocks);
        return Math.max(0L, Math.max(boundedPruneHeight, blocks - 12L));
    }

    private Map<String, Object> mergeTriggerState(String status, String reason) {
        Map<String, Object> result = new LinkedHashMap<>(lastSyncTrigger);
        result.put("status", status);
        result.put("reason", reason);
        return result;
    }

    private Map<String, Object> walletScanningState(JsonNode scanning) {
        if (scanning == null || scanning.isMissingNode() || scanning.isNull()) {
            return Map.of("active", false);
        }
        if (scanning.isObject()) {
            return Map.of(
                    "active", scanning.path("progress").asDouble(1.0d) < 1.0d,
                    "durationSeconds", scanning.path("duration").asLong(0L),
                    "progress", scanning.path("progress").asDouble(0.0d));
        }
        return Map.of("active", scanning.asBoolean(false));
    }

    private JsonNode nodeRpc(BlockchainClient client, String method, Object... params) {
        if (client instanceof BitcoinCoreRpcClient bitcoinCore) {
            return unwrap(bitcoinCore.executeNodeRpc(method, params));
        }
        return unwrap(client.executeRpc(method, params));
    }

    private JsonNode safeNodeRpc(BlockchainClient client, String method, Object... params) {
        try {
            return nodeRpc(client, method, params);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private JsonNode safeRpc(BlockchainClient client, String method, Object... params) {
        try {
            return unwrap(client.executeRpc(method, params));
        } catch (RuntimeException exception) {
            return null;
        }
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
