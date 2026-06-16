package source.sovereign.quorum;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QuorumShardService {

    private static final Logger log = LoggerFactory.getLogger(QuorumShardService.class);
    private static final String SHA_256_HEX = "^[0-9a-fA-F]{64}$";

    private final Map<String, Instant> prepared = new ConcurrentHashMap<>();
    private final Map<String, Instant> committed = new ConcurrentHashMap<>();
    private final Duration entryTtl;

    public QuorumShardService(@Value("${quorum.shard.entry-ttl-ms:300000}") long entryTtlMs) {
        this.entryTtl = Duration.ofMillis(Math.max(30_000, entryTtlMs));
    }

    public Ack health() {
        cleanup();
        return new Ack(true, "healthy");
    }

    public Ack prepare(String txHash, String remoteShardId) {
        cleanup();
        requireValidHash(txHash);
        prepared.putIfAbsent(txHash, Instant.now());
        log.info("[Quorum Shard] PREPARE accepted txHash={} from={}", txHash, remoteShardId);
        return new Ack(true, "prepared");
    }

    public Ack commit(String txHash, String remoteShardId) {
        cleanup();
        requireValidHash(txHash);
        if (committed.containsKey(txHash)) {
            return new Ack(true, "committed");
        }
        if (!prepared.containsKey(txHash)) {
            log.warn("[Quorum Shard] COMMIT accepted without local PREPARE txHash={} from={}."
                    + " Treating as idempotent recovery after restart.", txHash, remoteShardId);
        }
        committed.putIfAbsent(txHash, Instant.now());
        prepared.remove(txHash);
        return new Ack(true, "committed");
    }

    private void requireValidHash(String txHash) {
        if (txHash == null || !txHash.matches(SHA_256_HEX)) {
            throw new IllegalArgumentException("Quorum txHash must be a SHA-256 hex digest.");
        }
    }

    private void cleanup() {
        Instant cutoff = Instant.now().minus(entryTtl);
        prepared.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        committed.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    public record Ack(boolean accepted, String status) {
    }
}
