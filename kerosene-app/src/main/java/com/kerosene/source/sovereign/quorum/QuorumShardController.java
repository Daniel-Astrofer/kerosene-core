package source.sovereign.quorum;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/quorum")
public class QuorumShardController {

    private static final String HEALTH_PATH = "/quorum/health";
    private static final String PREPARE_PATH = "/quorum/prepare";
    private static final String COMMIT_PATH = "/quorum/commit";

    private final QuorumShardService shardService;
    private final QuorumAttestationService attestationService;

    public QuorumShardController(
            QuorumShardService shardService,
            QuorumAttestationService attestationService) {
        this.shardService = shardService;
        this.attestationService = attestationService;
    }

    @PostMapping("/health")
    public ResponseEntity<Map<String, Object>> health(
            @RequestBody QuorumRequest request,
            @RequestHeader(value = "X-Tx-Hash", required = false) String txHashHeader,
            @RequestHeader(value = QuorumAttestationService.SHARD_ID_HEADER, required = false) String shardId,
            @RequestHeader(value = QuorumAttestationService.TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = QuorumAttestationService.NONCE_HEADER, required = false) String nonce,
            @RequestHeader(value = QuorumAttestationService.SIGNATURE_HEADER, required = false) String signature) {
        if (!verify(HEALTH_PATH, request, txHashHeader, shardId, timestamp, nonce, signature)) {
            return forbidden();
        }
        QuorumShardService.Ack ack = shardService.health();
        return ok(ack, shardId);
    }

    @PostMapping("/prepare")
    public ResponseEntity<Map<String, Object>> prepare(
            @RequestBody QuorumRequest request,
            @RequestHeader(value = "X-Tx-Hash", required = false) String txHashHeader,
            @RequestHeader(value = QuorumAttestationService.SHARD_ID_HEADER, required = false) String shardId,
            @RequestHeader(value = QuorumAttestationService.TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = QuorumAttestationService.NONCE_HEADER, required = false) String nonce,
            @RequestHeader(value = QuorumAttestationService.SIGNATURE_HEADER, required = false) String signature) {
        if (!verify(PREPARE_PATH, request, txHashHeader, shardId, timestamp, nonce, signature)) {
            return forbidden();
        }
        QuorumShardService.Ack ack = shardService.prepare(request.txHash(), shardId);
        return ok(ack, shardId);
    }

    @PostMapping("/commit")
    public ResponseEntity<Map<String, Object>> commit(
            @RequestBody QuorumRequest request,
            @RequestHeader(value = "X-Tx-Hash", required = false) String txHashHeader,
            @RequestHeader(value = QuorumAttestationService.SHARD_ID_HEADER, required = false) String shardId,
            @RequestHeader(value = QuorumAttestationService.TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = QuorumAttestationService.NONCE_HEADER, required = false) String nonce,
            @RequestHeader(value = QuorumAttestationService.SIGNATURE_HEADER, required = false) String signature) {
        if (!verify(COMMIT_PATH, request, txHashHeader, shardId, timestamp, nonce, signature)) {
            return forbidden();
        }
        QuorumShardService.Ack ack = shardService.commit(request.txHash(), shardId);
        return ok(ack, shardId);
    }

    private boolean verify(
            String path,
            QuorumRequest request,
            String txHashHeader,
            String shardId,
            String timestamp,
            String nonce,
            String signature) {
        if (request == null || request.txHash() == null || !request.txHash().equals(txHashHeader)) {
            return false;
        }
        return attestationService.verify(path, request.txHash(), shardId, timestamp, nonce, signature);
    }

    private ResponseEntity<Map<String, Object>> ok(QuorumShardService.Ack ack, String shardId) {
        return ResponseEntity.ok(Map.of(
                "accepted", ack.accepted(),
                "status", ack.status(),
                "peer", shardId != null ? shardId : "unknown"));
    }

    private ResponseEntity<Map<String, Object>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("accepted", false, "status", "forbidden"));
    }

    public record QuorumRequest(String txHash) {
    }
}
