package source.sovereign.quorum;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class TorMtlsQuorumTransport implements QuorumTransport {

    private static final Logger logger = LoggerFactory.getLogger(TorMtlsQuorumTransport.class);
    private static final String PREPARE_PATH = "/quorum/prepare";
    private static final String COMMIT_PATH = "/quorum/commit";
    private static final String HEALTH_PATH = "/quorum/health";

    private final TorMtlsService torMtlsService;
    private final ObjectMapper objectMapper;
    private final long ackTimeoutMs;
    private final ExecutorService executorService;

    public TorMtlsQuorumTransport(
            TorMtlsService torMtlsService,
            ObjectMapper objectMapper,
            @Value("${quorum.shard.ack-timeout-ms:5000}") long ackTimeoutMs) {
        this.torMtlsService = torMtlsService;
        this.objectMapper = objectMapper;
        this.ackTimeoutMs = ackTimeoutMs;
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public QuorumPhaseResult prepare(String transactionHash, QuorumTopology topology) {
        return sendPhase(QuorumPhase.PREPARE, PREPARE_PATH, transactionHash, topology);
    }

    @Override
    public QuorumPhaseResult commit(String transactionHash, QuorumTopology topology) {
        return sendPhase(QuorumPhase.COMMIT, COMMIT_PATH, transactionHash, topology);
    }

    @Override
    public QuorumPhaseResult healthCheck(QuorumTopology topology) {
        return sendPhase(QuorumPhase.HEALTH_CHECK, HEALTH_PATH, "HEALTH_CHECK", topology);
    }

    @PreDestroy
    void closeExecutor() {
        executorService.shutdownNow();
    }

    private QuorumPhaseResult sendPhase(
            QuorumPhase phase,
            String path,
            String transactionHash,
            QuorumTopology topology) {
        List<CompletableFuture<QuorumPeerResult>> futures = topology.remotePeers().stream()
                .map(peer -> CompletableFuture.supplyAsync(
                        () -> postToPeer(peer, path, transactionHash), executorService)
                        .completeOnTimeout(QuorumPeerResult.timeout(peer), ackTimeoutMs, TimeUnit.MILLISECONDS)
                        .exceptionally(error -> QuorumPeerResult.failed(peer, rootMessage(error))))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<QuorumPeerResult> peerResults = futures.stream()
                .map(CompletableFuture::join)
                .toList();
        int acceptedNodes = topology.localNodeCount()
                + (int) peerResults.stream().filter(QuorumPeerResult::accepted).count();
        boolean timedOut = peerResults.stream().anyMatch(QuorumPeerResult::timedOut);

        logger.debug("[Quorum Transport] {} completed with {}/{} accepted nodes.",
                phase, acceptedNodes, topology.totalNodes());

        return new QuorumPhaseResult(
                phase,
                acceptedNodes,
                topology.totalNodes(),
                topology.requiredQuorum(),
                timedOut,
                peerResults);
    }

    private QuorumPeerResult postToPeer(QuorumPeer peer, String path, String transactionHash) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of("txHash", transactionHash));
            TorMtlsService.QuorumResponse response = torMtlsService.post(
                    peer.endpoint(path),
                    transactionHash,
                    payload);
            if (response.statusCode() == HttpStatus.OK.value()) {
                logger.debug("[Quorum Transport] ACK from {}", peer.baseUrl());
                return QuorumPeerResult.accepted(peer, response.statusCode());
            }

            logger.warn("[Quorum Transport] NACK from {} (HTTP {}).", peer.baseUrl(), response.statusCode());
            return QuorumPeerResult.rejected(peer, response.statusCode());
        } catch (JsonProcessingException e) {
            logger.error("[Quorum Transport] Could not encode quorum payload for {}: {}",
                    peer.baseUrl(), e.getMessage());
            return QuorumPeerResult.failed(peer, e.getMessage());
        } catch (IOException e) {
            logger.warn("[Quorum Transport] No response from {}: {}", peer.baseUrl(), e.getMessage());
            return QuorumPeerResult.failed(peer, e.getMessage());
        }
    }

    private String rootMessage(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage();
    }
}
