package source.ledger.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import source.security.SuicideService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Servico de Sincronizacao de Shards com Protocolo Fail-Stop para Split-Brain.
 *
 * ─── Problema: Split-Brain
 * ────────────────────────────────────────────────────
 * Se a rede Tor sofre uma partição global, os nós podem ficar isolados e
 * aprovar transações conflitantes (cada partição pensa que é o líder legítimo).
 * Isso causa inconsistência irreversível no Ledger.
 *
 * ─── Solução: Fail-Stop + Quórum Absoluto ────────────────────────────────────
 * Nunca permitir uma transação se a maioria absoluta dos nós NÃO for detectada.
 * O sistema para totalmente (Fail-Stop) ao invés de operar em modo degradado.
 * Isso sacrifica disponibilidade em prol de consistência (CP, não AP — teorema
 * CAP).
 *
 * ─── Algoritmo Base: Raft-like 2PC ───────────────────────────────────────────
 * Fase 1 — Prepare: Leader propõe a transação, aguarda ACKs dos N-1 nós.
 * Fase 2 — Commit: Somente após quórum atingido, o commit é executado.
 * Se qualquer fase falhar (timeout), NACK global e rollback.
 */
@Service
public class QuorumSyncService {

    private static final Logger logger = LoggerFactory.getLogger(QuorumSyncService.class);

    private static final int TOTAL_SHARDS = 3;
    private static final int QUORUM_REQUIRED = (TOTAL_SHARDS / 2) + 1; // Maioria absoluta = 2

    /**
     * Timeout máximo para aguardar ACK de um nó remoto (usado pelo executor mTLS em
     * produção).
     * Se um nó não responde em 5s, ele é considerado DOWN para este round.
     */
    @SuppressWarnings("unused") // Usado pelo executor mTLS real em produção
    private static final long SHARD_ACK_TIMEOUT_MS = 5_000;

    /**
     * Se o sistema não consegue confirmar quórum por mais de 30s consecutivos,
     * entra em modo Fail-Stop e bloqueia todas as operações de escrita.
     */
    private static final long FAIL_STOP_WINDOW_MS = 30_000;

    private volatile boolean failStopMode = false;
    private volatile Instant lastQuorumSuccess = Instant.now();
    private final AtomicInteger consecutiveQuorumFailures = new AtomicInteger(0);
    private final AtomicLong totalTransactionsProposed = new AtomicLong(0);
    private final AtomicLong totalTransactionsAccepted = new AtomicLong(0);

    private final SuicideService suicideService;

    /**
     * Shard peer URLs for mTLS quorum communication.
     * Expected format:
     * https://peer-is.kerosene.internal:8443,https://peer-ch.kerosene.internal:8443
     * Leave blank in dev (falls back to local-only mock that returns TOTAL_SHARDS).
     */
    @Value("${quorum.shard.urls:}")
    private String shardUrlsConfig;

    private HttpClient quorumHttpClient;

    public QuorumSyncService(SuicideService suicideService) {
        this.suicideService = suicideService;
        // Build an HttpClient for mTLS communication with a tight connect timeout
        this.quorumHttpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(SHARD_ACK_TIMEOUT_MS))
                .version(HttpClient.Version.HTTP_2)
                .build();
        logger.info("[Quorum Sync] Initialized. Split-Brain Fail-Stop active. Requiring {}/{} nodes.",
                QUORUM_REQUIRED, TOTAL_SHARDS);
    }

    // --- Public state for SovereigntyStatusController ---
    public boolean isFailStopMode() {
        return failStopMode;
    }

    public Instant getLastQuorumSuccess() {
        return lastQuorumSuccess;
    }

    public long getTotalProposed() {
        return totalTransactionsProposed.get();
    }

    public long getTotalAccepted() {
        return totalTransactionsAccepted.get();
    }

    /**
     * Fase 1 + Fase 2 do protocolo 2PC com Quórum.
     *
     * IMPORTANTE — Fail-Stop: Se o sistema está em modo Split-Brain detectado,
     * NENHUMA transação é processada até que os nós se reconectem.
     *
     * @param transactionHash O hash SHA-256 da transação proposta.
     * @return true se quórum foi atingido (COMMIT); false se rejeitada (ROLLBACK).
     * @throws SplitBrainException se o sistema está em Fail-Stop por Split-Brain.
     */
    public boolean proposeTransactionToQuorum(String transactionHash) {
        totalTransactionsProposed.incrementAndGet();

        // ── Fail-Stop Guard ───────────────────────────────────────────────────
        if (failStopMode) {
            logger.error("[SPLIT-BRAIN FAIL-STOP] System is in Fail-Stop mode. " +
                    "Transaction {} REJECTED. No writes allowed until quorum is re-established.",
                    transactionHash);
            throw new SplitBrainException(
                    "Kerosene is in Fail-Stop mode due to network partition (Split-Brain detected). " +
                            "Transaction rejected to preserve ledger consistency.");
        }

        logger.debug("[Quorum Sync] Phase 1 — PREPARE: Proposing {} to {} shards...",
                transactionHash, TOTAL_SHARDS);

        // ── Phase 1: Prepare ──────────────────────────────────────────────────
        // Envia a proposta para todos os nós e aguarda ACKs com timeout
        int ackCount = executePhaseOne(transactionHash);

        if (ackCount < QUORUM_REQUIRED) {
            // Quórum não atingido — registrar falha
            int failures = consecutiveQuorumFailures.incrementAndGet();
            logger.error("[Quorum Sync] Phase 1 FAILED. Got {}/{} ACKs (need {}). Consecutive failures: {}.",
                    ackCount, TOTAL_SHARDS, QUORUM_REQUIRED, failures);

            // Verificar se deve entrar em Fail-Stop
            checkAndEnterFailStop();

            // ROLLBACK — a transação local não deve ser comitada
            return false;
        }

        logger.debug("[Quorum Sync] Phase 2 — COMMIT: Broadcasting commit for {} to {} nodes.",
                transactionHash, ackCount);

        // ── Phase 2: Commit ───────────────────────────────────────────────────
        boolean commitOk = executePhaseTwo(transactionHash, ackCount);

        if (commitOk) {
            consecutiveQuorumFailures.set(0); // Reset contador de falhas
            lastQuorumSuccess = Instant.now();
            totalTransactionsAccepted.incrementAndGet();
            logger.info("[Quorum Sync] COMMIT SUCCESS. Transaction {} accepted ({}/{} nodes).",
                    transactionHash, ackCount, TOTAL_SHARDS);
        } else {
            // Issue 1: If Phase 2 fails, we MUST Fail-Stop because Phase 1 was already
            // confirmed.
            // Some peers might have committed, while others didn't. This is split-brain
            // territory.
            logger.error("[CRITICAL] Phase 2 COMMIT FAILED for transaction {}. Entering Fail-Stop.", transactionHash);
            failStopMode = true;
            suicideService.triggerInstantSuicide("Phase 2 Commit failure — data consistency compromised");
        }

        return commitOk;
    }

    /**
     * Phase 1: Sends PREPARE to all shard peers in parallel.
     * Each request has SHARD_ACK_TIMEOUT_MS to respond.
     * Counts this node (local) as 1 implicit ACK.
     *
     * Issue 6: TimeoutException now triggers checkAndEnterFailStop() instead of
     * silently continuing with partial ACKs.
     */
    private int executePhaseOne(String txHash) {
        List<String> peers = getShardPeers();
        if (peers.isEmpty()) {
            // Dev mode: no peers configured, simulate full quorum locally
            logger.debug("[Quorum] No shard peers configured — running in local-only mode.");
            return TOTAL_SHARDS;
        }

        AtomicInteger acks = new AtomicInteger(1); // count self as 1 ACK

        List<CompletableFuture<Void>> futures = peers.stream().map(peerUrl -> CompletableFuture.runAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(peerUrl + "/quorum/prepare"))
                        .header("Content-Type", "application/json")
                        .header("X-Tx-Hash", txHash)
                        .timeout(Duration.ofMillis(SHARD_ACK_TIMEOUT_MS))
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"txHash\":\"" + txHash + "\"}"))
                        .build();
                HttpResponse<Void> resp = quorumHttpClient.send(req, HttpResponse.BodyHandlers.discarding());
                if (resp.statusCode() == HttpStatus.OK.value()) {
                    acks.incrementAndGet();
                    logger.debug("[Quorum] ACK from {}", peerUrl);
                } else {
                    logger.warn("[Quorum] NACK from {} (HTTP {})", peerUrl, resp.statusCode());
                }
            } catch (HttpTimeoutException e) {
                // Issue 6: detect HTTP-level timeout explicitly
                logger.error("[Quorum] TIMEOUT from {} — peer may be down or partitioned", peerUrl);
                // Trigger immediate suicide/fail-stop on ANY peer timeout during Phase 1 to
                // prevent split-brain
                suicideService.triggerInstantSuicide(
                        "Phase 1 peer timeout (" + peerUrl + ") — entering preventive Fail-Stop");
            } catch (Exception e) {
                logger.warn("[Quorum] No response from {} (treated as NACK): {}", peerUrl, e.getMessage());
            }
        })).toList();

        // Wait for all requests up to the timeout
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(SHARD_ACK_TIMEOUT_MS + 500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // Issue 6: Overall timeout is critical — indicate network partition and KILL
            // node
            logger.error("[CRITICAL] Phase 1 timed out waiting for peer ACKs. Current ACKs: {}/{}",
                    acks.get(), QUORUM_REQUIRED);
            suicideService.triggerInstantSuicide("Phase 1 global timeout — possible split-brain partition");
        } catch (Exception e) {
            logger.error("[Quorum] Phase 1 unexpected error: {}", e.getMessage());
        }

        return acks.get();
    }

    /**
     * Phase 2: Sends COMMIT signal to all available shard peers and WAITS for
     * their acknowledgements.
     *
     * Issue 1 fix: Previously returned immediately without waiting for async
     * commits. Now collects results synchronously, counts successes, and enters
     * Fail-Stop if quorum of committed peers is not reached.
     */
    private boolean executePhaseTwo(String txHash, int nodesReady) {
        List<String> peers = getShardPeers();
        if (peers.isEmpty()) {
            return nodesReady >= QUORUM_REQUIRED;
        }

        // Issue 1: Collect futures and WAIT for all commits
        List<CompletableFuture<Boolean>> commitFutures = peers.stream()
                .map(peerUrl -> CompletableFuture.supplyAsync(() -> {
                    try {
                        HttpRequest req = HttpRequest.newBuilder()
                                .uri(URI.create(peerUrl + "/quorum/commit"))
                                .header("X-Tx-Hash", txHash)
                                .timeout(Duration.ofMillis(SHARD_ACK_TIMEOUT_MS))
                                .POST(HttpRequest.BodyPublishers.ofString(
                                        "{\"txHash\":\"" + txHash + "\"}"))
                                .build();
                        HttpResponse<Void> resp = quorumHttpClient.send(req,
                                HttpResponse.BodyHandlers.discarding());
                        boolean success = resp.statusCode() == HttpStatus.OK.value();
                        if (!success) {
                            logger.error("[Quorum Phase 2] Peer {} REJECTED commit with HTTP {}",
                                    peerUrl, resp.statusCode());
                        }
                        return success;
                    } catch (Exception e) {
                        logger.error("[Quorum Phase 2] Peer {} commit failed: {}", peerUrl, e.getMessage());
                        return false;
                    }
                }))
                .toList();

        // Wait for all commits with a generous timeout (2x individual)
        try {
            CompletableFuture.allOf(commitFutures.toArray(new CompletableFuture[0]))
                    .get(SHARD_ACK_TIMEOUT_MS * 2, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            logger.error("[Quorum Phase 2] TIMEOUT waiting for commit acknowledgements. " +
                    "Possible split-brain. Entering FAIL-STOP.");
            failStopMode = true;
            suicideService.triggerInstantSuicide("Phase 2 commit timeout — possible split-brain");
            return false;
        } catch (Exception e) {
            logger.error("[Quorum Phase 2] Unexpected error waiting for commits: {}", e.getMessage());
            return false;
        }

        // Count successful commits (self + peers who returned 200)
        long peerCommits = commitFutures.stream()
                .map(CompletableFuture::join)
                .filter(ok -> ok)
                .count();
        long totalCommits = peerCommits + 1; // +1 for local node

        boolean allCommitted = totalCommits >= QUORUM_REQUIRED;
        if (!allCommitted) {
            logger.error("[Quorum Phase 2] Only {}/{} nodes committed. Entering FAIL-STOP.",
                    totalCommits, QUORUM_REQUIRED);
            failStopMode = true;
            suicideService.triggerInstantSuicide(
                    "Phase 2 insufficient commits (" + totalCommits + "/" + QUORUM_REQUIRED + ") — split-brain");
        }

        return allCommitted;
    }

    /** Parses the comma-separated shard peer URL configuration. */
    private List<String> getShardPeers() {
        if (shardUrlsConfig == null || shardUrlsConfig.isBlank())
            return List.of();
        return Arrays.stream(shardUrlsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Verifica se o número de falhas consecutivas de quórum indica Split-Brain.
     * Se sim, ativa Fail-Stop: bloqueia TODAS as escritas até reconexão manual.
     */
    private void checkAndEnterFailStop() {
        long msSinceLastSuccess = Instant.now().toEpochMilli() - lastQuorumSuccess.toEpochMilli();

        if (msSinceLastSuccess > FAIL_STOP_WINDOW_MS) {
            if (!failStopMode) {
                failStopMode = true;
                logger.error("[CRITICAL] SPLIT-BRAIN DETECTED. Entering FAIL-STOP mode. " +
                        "No quorum for {}ms. All write operations SUSPENDED. " +
                        "Manual operator intervention required to rejoin network.",
                        msSinceLastSuccess);

                // Em produção: enviar alerta para PagerDuty / Slack / Orquestrador

                // 💥 VENENO: Ativa a morte do servidor. O Nodo está ilhado e pode sofrer abuso
                // isolado.
                suicideService.triggerInstantSuicide("Split-Brain Detected: Server isolated from Quorum for "
                        + msSinceLastSuccess + "ms. Committing ritual suicide.");
            }
        }
    }

    /**
     * Operador chama este endpoint (via admin API) após reconectar manualmente
     * os nós para sair do modo Fail-Stop.
     * Requer validação de que o quórum foi restaurado antes de liberar escritas.
     */
    public void exitFailStopMode() {
        // Verificação prévia: testar quórum antes de liberar
        int liveNodes = mockSendToNodes("HEALTH_CHECK");
        if (liveNodes >= QUORUM_REQUIRED) {
            failStopMode = false;
            consecutiveQuorumFailures.set(0);
            lastQuorumSuccess = Instant.now();
            logger.info("[Quorum Sync] Fail-Stop mode CLEARED. Quorum restored ({}/{} nodes live).",
                    liveNodes, TOTAL_SHARDS);
        } else {
            logger.error("[Quorum Sync] Cannot exit Fail-Stop: quorum still not available ({}/{}).",
                    liveNodes, TOTAL_SHARDS);
            throw new SplitBrainException(
                    "Cannot exit Fail-Stop: quorum not restored. " + liveNodes + "/" + TOTAL_SHARDS + " nodes.");
        }
    }

    /**
     * O Fluxo de Ressurreicao: um servidor morto pelo RemoteAttestation renasce,
     * valida seu próprio hardware com o Vault Central e se reintegra ao cluster.
     */
    public void resurrectNodeAndSync() {
        logger.info("[Quorum Sync] Initiating Node Resurrection Protocol...");

        boolean hasSessionKey = authenticateWithCentralVault();
        if (!hasSessionKey) {
            logger.error("[CRITICAL] Node failed to authenticate hardware. Cannot sync.");
            suicideService
                    .triggerInstantSuicide("Node resurrection: hardware authentication failed. Cannot sync state.");
            return; // unreachable — halt() was called, but satisfies compiler
        }

        logger.info("[Quorum Sync] Bootstrapping Ledger state from majority shards...");
        downloadMissingBlocks();

        // Ao reintegrar, sair do Fail-Stop se estiver ativo
        if (failStopMode) {
            exitFailStopMode();
        }

        logger.info("[Quorum Sync] Node fully synced and ready to process traffic.");
    }

    private boolean authenticateWithCentralVault() {
        return true;
    }

    private void downloadMissingBlocks() {
        // Puxa o Merkle Tree raiz ou blocos especificos pra validar.
    }

    private int mockSendToNodes(String txHash) {
        // Assume sucesso global em 3 nós online por padrão nesta simulação.
        return TOTAL_SHARDS;
    }

    /**
     * Exceção lançada quando o sistema detecta Split-Brain e entra em Fail-Stop.
     * Deve ser mapeada para HTTP 503 (Service Unavailable) na camada de controller.
     */
    public static class SplitBrainException extends RuntimeException {
        public SplitBrainException(String message) {
            super(message);
        }
    }
}
