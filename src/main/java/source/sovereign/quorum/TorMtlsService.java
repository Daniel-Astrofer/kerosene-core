package source.sovereign.quorum;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.io.*;
import java.net.URI;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * ─── TOR MTLS TRANSPORT ──────────────────────────────────────────────────────
 *
 * Implements military-grade zero-trust communication between shards:
 * 1. Tunnel established via Tor SOCKS5 over Unix Domain Socket (UDS).
 * 2. TLS 1.3 handshake performed over the Tor tunnel.
 * 3. Mutual TLS (mTLS): Client authenticates with certificate; Server authenticates with certificate.
 * 4. Double encryption: TLS over Tor circuit.
 *
 * This prevents ANY network observer (including Tor nodes) from seeing the payload,
 * and ensures only authorized shards can speak to each other.
 */
@Service
public class TorMtlsService {

    private static final Logger log = LoggerFactory.getLogger(TorMtlsService.class);

    @Value("${tor.socks.path:/var/run/tor/socks/tor.sock}")
    private String torSocksPath;

    @Value("${certs.path:/certs}")
    private String certsDir;

    private final QuorumAttestationService attestationService;
    private SSLSocketFactory sslSocketFactory;
    private SSLContext sslContext;
    private volatile java.net.http.HttpClient directHttpsClient;

    public TorMtlsService(QuorumAttestationService attestationService) {
        this.attestationService = attestationService;
    }

    private synchronized void ensureSslInitialized() throws Exception {
        if (sslSocketFactory != null && sslContext != null && directHttpsClient != null) return;

        log.info("[Tor-mTLS] Initializing SSL Context from certificates in {}...", certsDir);

        // 1. Load Root CA (TrustStore)
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate caCert;
        try (InputStream is = new FileInputStream(certsDir + "/rootCA.crt")) {
            caCert = (X509Certificate) cf.generateCertificate(is);
        }

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("root-ca", caCert);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        // 2. Load Client Cert & Key (mTLS)
        CertificateFactory cfCert = CertificateFactory.getInstance("X.509");
        X509Certificate clientCert;
        try (InputStream is = new FileInputStream(certsDir + "/client.crt")) {
            clientCert = (X509Certificate) cfCert.generateCertificate(is);
        }

        // Load DER-encoded Private Key (PKCS#8)
        byte[] keyBytes;
        try (InputStream is = new FileInputStream(certsDir + "/client.key.der")) {
            keyBytes = is.readAllBytes();
        }

        KeyFactory kf = KeyFactory.getInstance("RSA"); // Assuming RSA as per common standards
        PrivateKey privateKey = kf.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(keyBytes));

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry("client-identity", privateKey, "password".toCharArray(), new java.security.cert.Certificate[]{clientCert});

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, "password".toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
        this.sslContext = sslContext;
        this.sslSocketFactory = sslContext.getSocketFactory();
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setProtocols(new String[] { "TLSv1.3" });
        sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
        this.directHttpsClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .sslContext(sslContext)
                .sslParameters(sslParameters)
                .build();
        log.info("[Tor-mTLS] SSL Context initialized (TLS 1.3)");
    }

    /**
     * Executes an authenticated quorum POST over mTLS.
     */
    public QuorumResponse post(String peerUrl, String txHash, String payload) throws IOException {
        try {
            URI uri = URI.create(peerUrl);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IOException("Peer URL has no host: " + peerUrl);
            }
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IOException("Quorum peers must use HTTPS with mTLS.");
            }
            if (!host.endsWith(".onion")) {
                return postDirect(uri, txHash, payload);
            }
            return postTlsOverTor(uri, txHash, payload);
        } catch (Exception e) {
            log.error("[Tor-mTLS] Request to {} failed: {}", peerUrl, e.getMessage());
            throw new IOException("Quorum transport failure: " + e.getMessage(), e);
        }
    }

    private QuorumResponse postDirect(URI uri, String txHash, String payload) throws IOException, InterruptedException {
        try {
            ensureSslInitialized();
        } catch (Exception exception) {
            throw new IOException("Unable to initialize quorum mTLS context", exception);
        }
        String path = pathWithQuery(uri);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header("Digest", digest(payload))
                .header("X-Tx-Hash", txHash)
                .POST(HttpRequest.BodyPublishers.ofString(payload));
        attestationService.signedHeaders(path, txHash).forEach(builder::header);
        HttpResponse<String> response = directHttpsClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new QuorumResponse(response.statusCode());
    }

    private QuorumResponse postTlsOverTor(URI uri, String txHash, String payload) throws Exception {
        ensureSslInitialized();

        String host = uri.getHost();
        int port = uri.getPort() == -1 ? 443 : uri.getPort();
        String path = pathWithQuery(uri);

        log.debug("[Tor-mTLS] Connecting to {} via Tor SOCKS UDS using TLS...", uri);

        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(UnixDomainSocketAddress.of(torSocksPath));
            performSocks5Handshake(channel, host, port);

            try (java.net.Socket rawSocket = channel.socket();
                 java.net.Socket sslSocket = sslSocketFactory.createSocket(rawSocket, host, port, true)) {
                SSLSocket tlsSocket = (SSLSocket) sslSocket;
                SSLParameters sslParameters = tlsSocket.getSSLParameters();
                sslParameters.setProtocols(new String[] { "TLSv1.3" });
                sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
                sslParameters.setServerNames(List.of(new SNIHostName(host)));
                tlsSocket.setSSLParameters(sslParameters);

                tlsSocket.startHandshake();
                log.debug("[Tor-mTLS] mTLS Handshake successful with {}", host);

                PrintWriter writer = new PrintWriter(new OutputStreamWriter(tlsSocket.getOutputStream(), StandardCharsets.UTF_8));
                writeHttpRequest(writer, host, path, txHash, payload);

                BufferedReader reader = new BufferedReader(new InputStreamReader(tlsSocket.getInputStream(), StandardCharsets.UTF_8));
                String line = reader.readLine();
                if (line == null) {
                    throw new IOException("Remote shard closed connection");
                }

                return new QuorumResponse(parseStatus(line));
            }
        }
    }

    private void performSocks5Handshake(SocketChannel channel, String host, int port) throws IOException {
        // VER=5, NMETHODS=1, METHOD=0x00
        ByteBuffer buf = ByteBuffer.wrap(new byte[]{0x05, 0x01, 0x00});
        while (buf.hasRemaining()) channel.write(buf);

        buf = ByteBuffer.allocate(2);
        readFully(channel, buf);
        if (buf.get(0) != 0x05 || buf.get(1) != 0x00) throw new IOException("SOCKS5 Greeting failed");

        // CONNECT CMD
        byte[] hostBytes = host.getBytes(StandardCharsets.US_ASCII);
        ByteBuffer req = ByteBuffer.allocate(7 + hostBytes.length);
        req.put((byte) 0x05); // VER
        req.put((byte) 0x01); // CMD = CONNECT
        req.put((byte) 0x00); // RSV
        req.put((byte) 0x03); // ATYP = DOMAINNAME
        req.put((byte) hostBytes.length);
        req.put(hostBytes);
        req.putShort((short) port);
        req.flip();
        while (req.hasRemaining()) channel.write(req);

        // Response
        buf = ByteBuffer.allocate(1024);
        readFully(channel, buf, 2);
        if (buf.get(1) != 0x00) throw new IOException("SOCKS5 Connect failed, code: " + buf.get(1));
    }

    private void writeHttpRequest(SocketChannel channel, String host, String path, String txHash, String payload)
            throws IOException {
        StringBuilder request = new StringBuilder();
        appendRequestHeaders(request, host, path, txHash, payload);
        ByteBuffer buffer = ByteBuffer.wrap(request.toString().getBytes(StandardCharsets.UTF_8));
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private void writeHttpRequest(PrintWriter writer, String host, String path, String txHash, String payload) {
        StringBuilder request = new StringBuilder();
        appendRequestHeaders(request, host, path, txHash, payload);
        writer.print(request);
        writer.flush();
    }

    private void appendRequestHeaders(StringBuilder request, String host, String path, String txHash, String payload) {
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        request.append("POST ").append(path).append(" HTTP/1.1\r\n");
        request.append("Host: ").append(host).append("\r\n");
        request.append("Connection: close\r\n");
        request.append("Content-Type: application/json\r\n");
        request.append("Digest: ").append(digest(payload)).append("\r\n");
        request.append("X-Tx-Hash: ").append(txHash).append("\r\n");
        for (Map.Entry<String, String> header : attestationService.signedHeaders(path, txHash).entrySet()) {
            request.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
        }
        request.append("Content-Length: ").append(payloadBytes.length).append("\r\n");
        request.append("\r\n");
        request.append(payload);
    }

    private int parseStatus(String statusLine) throws IOException {
        String[] parts = statusLine.split(" ");
        if (parts.length < 2) {
            throw new IOException("Invalid HTTP status line: " + statusLine);
        }
        return Integer.parseInt(parts[1]);
    }

    private String pathWithQuery(URI uri) {
        String path = uri.getRawPath();
        if (path == null || path.isBlank()) {
            path = "/";
        }
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }

    private String digest(String payload) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8));
            return "SHA-256=" + Base64.getEncoder().encodeToString(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private void readFully(SocketChannel channel, ByteBuffer buffer) throws IOException {
        readFully(channel, buffer, buffer.remaining());
    }

    private void readFully(SocketChannel channel, ByteBuffer buffer, int minimumBytes) throws IOException {
        int readTotal = 0;
        while (readTotal < minimumBytes) {
            int read = channel.read(buffer);
            if (read < 0) {
                throw new IOException("SOCKS5 proxy closed connection");
            }
            readTotal += read;
        }
    }

    public record QuorumResponse(int statusCode) {}
}
