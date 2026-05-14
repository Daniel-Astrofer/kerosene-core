package source.transactions.infra;

import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import source.mpc.grpc.KeygenRequest;
import source.mpc.grpc.KeygenResponse;
import source.mpc.grpc.MpcServiceGrpc;
import source.mpc.grpc.SignRequest;
import source.mpc.grpc.SignResponse;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import source.auth.AuthExceptions;

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
    private final long requestTimeoutMs;
    private ManagedChannel channel;
    private MpcServiceGrpc.MpcServiceBlockingStub blockingStub;

    public MpcSidecarClient(
            @Value("${mpc.sidecar.host:localhost}") String host,
            @Value("${mpc.sidecar.port:50051}") int port,
            @Value("${mpc.sidecar.tls.enabled:true}") boolean tlsEnabled,
            @Value("${mpc.sidecar.tls.cert-chain:}") String certChainPath,
            @Value("${mpc.sidecar.tls.private-key:}") String privateKeyPath,
            @Value("${mpc.sidecar.tls.trust-cert-collection:}") String trustCertCollectionPath,
            @Value("${mpc.sidecar.request-timeout-ms:5000}") long requestTimeoutMs) {
        this.host = host;
        this.port = port;
        this.tlsEnabled = tlsEnabled;
        this.certChainPath = certChainPath;
        this.privateKeyPath = privateKeyPath;
        this.trustCertCollectionPath = trustCertCollectionPath;
        this.requestTimeoutMs = requestTimeoutMs;
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
        this.blockingStub = MpcServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        if (channel != null) {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    public String keygen(String userId, int threshold, int totalParties) {
        log.info("[MPC Client] Requesting Keygen for user {}", userId);
        KeygenRequest request = KeygenRequest.newBuilder()
                .setUserId(userId)
                .setThreshold(threshold)
                .setTotalParties(totalParties)
                .build();
        try {
            KeygenResponse response = stub().keygen(request);
            if (!response.getSuccess()) {
                throw new AuthExceptions.AuthValidationException(
                        "MPC keygen failed: " + response.getErrorMessage());
            }
            return response.getPublicKey();
        } catch (StatusRuntimeException exception) {
            throw new AuthExceptions.AuthValidationException(
                    "MPC keygen gRPC call failed: " + exception.getStatus().getCode());
        }
    }

    public byte[] sign(String userId, byte[] messageHash, String publicKey) {
        log.info("[MPC Client] Requesting Sign for hash of length {}", messageHash.length);
        SignRequest request = SignRequest.newBuilder()
                .setUserId(userId)
                .setMessageHash(ByteString.copyFrom(messageHash))
                .setPublicKey(publicKey == null ? "" : publicKey)
                .build();
        try {
            SignResponse response = stub().sign(request);
            if (!response.getSuccess()) {
                throw new AuthExceptions.AuthValidationException(
                        "MPC signing failed: " + response.getErrorMessage());
            }
            return response.getSignature().toByteArray();
        } catch (StatusRuntimeException exception) {
            throw new AuthExceptions.AuthValidationException(
                    "MPC signing gRPC call failed: " + exception.getStatus().getCode());
        }
    }

    public boolean isInitialized() {
        return channel != null && !channel.isShutdown() && blockingStub != null;
    }

    private MpcServiceGrpc.MpcServiceBlockingStub stub() {
        if (!isInitialized()) {
            throw new AuthExceptions.AuthValidationException("MPC sidecar client is not initialized.");
        }
        return blockingStub.withDeadlineAfter(requestTimeoutMs, TimeUnit.MILLISECONDS);
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
