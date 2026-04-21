package source.transactions.infra;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
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
    private final boolean tlsEnabled;
    private final String certChainPath;
    private final String privateKeyPath;
    private final String trustCertCollectionPath;
    private ManagedChannel channel;

    public MpcSidecarClient(
            @Value("${mpc.sidecar.host:localhost}") String host,
            @Value("${mpc.sidecar.port:50051}") int port,
            @Value("${mpc.sidecar.tls.enabled:true}") boolean tlsEnabled,
            @Value("${mpc.sidecar.tls.cert-chain:}") String certChainPath,
            @Value("${mpc.sidecar.tls.private-key:}") String privateKeyPath,
            @Value("${mpc.sidecar.tls.trust-cert-collection:}") String trustCertCollectionPath) {
        this.host = host;
        this.port = port;
        this.tlsEnabled = tlsEnabled;
        this.certChainPath = certChainPath;
        this.privateKeyPath = privateKeyPath;
        this.trustCertCollectionPath = trustCertCollectionPath;
    }

    @PostConstruct
    public void init() {
        log.info("Connecting to MPC Sidecar at {}:{} (mTLS={})", host, port, tlsEnabled);
        NettyChannelBuilder builder = NettyChannelBuilder.forAddress(host, port);
        if (tlsEnabled) {
            this.channel = builder.sslContext(buildClientSslContext()).build();
        } else {
            log.warn("[MPC Client] Plaintext gRPC is enabled. This is only acceptable in tests/local dev.");
            this.channel = builder.usePlaintext().build();
        }
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
        throw new IllegalStateException(
                "MPC keygen requires the generated mTLS gRPC stub. Refusing to return a placeholder public key.");
    }

    /**
     * Placeholder for Sign call.
     */
    public byte[] sign(String userId, byte[] messageHash, String publicKey) {
        log.info("[MPC Client] Requesting Sign for hash of length {}", messageHash.length);
        throw new IllegalStateException(
                "MPC signing requires the generated mTLS gRPC stub. Refusing to return a placeholder signature.");
    }

    private SslContext buildClientSslContext() {
        File certChain = requireReadableFile(certChainPath, "mpc.sidecar.tls.cert-chain");
        File privateKey = requireReadableFile(privateKeyPath, "mpc.sidecar.tls.private-key");
        File trustCertCollection = requireReadableFile(
                trustCertCollectionPath,
                "mpc.sidecar.tls.trust-cert-collection");
        try {
            return GrpcSslContexts.forClient()
                    .keyManager(certChain, privateKey)
                    .trustManager(trustCertCollection)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to configure mTLS for MPC sidecar client", e);
        }
    }

    private File requireReadableFile(String path, String propertyName) {
        if (path == null || path.isBlank()) {
            throw new IllegalStateException(propertyName + " is required when mpc.sidecar.tls.enabled=true");
        }
        File file = new File(path);
        if (!file.isFile() || !file.canRead()) {
            throw new IllegalStateException(propertyName + " must point to a readable file: " + path);
        }
        return file;
    }
}
