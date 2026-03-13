package source.transactions.infra;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/**
 * gRPC Client to communicate with the Go MPC Sidecar.
 * Handles distributed key generation and threshold signing.
 */
@Component
public class MpcSidecarClient {

    private static final Logger log = LoggerFactory.getLogger(MpcSidecarClient.class);

    private final String host;
    private final int port;
    private ManagedChannel channel;

    public MpcSidecarClient(
            @Value("${mpc.sidecar.host:localhost}") String host,
            @Value("${mpc.sidecar.port:50051}") int port) {
        this.host = host;
        this.port = port;
    }

    @PostConstruct
    public void init() {
        log.info("Connecting to MPC Sidecar at {}:{}", host, port);
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext() // For local/internal communication via Unix Sockets or localhost
                .build();
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        if (channel != null) {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /**
     * Placeholder for Keygen call.
     * In a real implementation, this would use the generated MpcServiceGrpc stub.
     */
    public String keygen(String userId, int threshold, int totalParties) {
        log.info("[MPC Client] Requesting Keygen for user {}", userId);
        // return stub.keygen(KeygenRequest.newBuilder()...).getPublicKey();
        return "MPC_GENERATED_PUBKEY_" + userId;
    }

    /**
     * Placeholder for Sign call.
     */
    public byte[] sign(String userId, byte[] messageHash, String publicKey) {
        log.info("[MPC Client] Requesting Sign for hash of length {}", messageHash.length);
        // return stub.sign(SignRequest.newBuilder()...).getSignature().toByteArray();
        return ("SIGNED_BY_MPC_" + userId).getBytes();
    }
}
