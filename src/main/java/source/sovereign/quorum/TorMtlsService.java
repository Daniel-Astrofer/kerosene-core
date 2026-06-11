package source.sovereign.quorum;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.io.*;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

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

    private SSLSocketFactory sslSocketFactory;

    public TorMtlsService() {
        // SSL socket factory will be initialized lazily or in @PostConstruct
    }

    private synchronized void ensureSslInitialized() throws Exception {
        if (sslSocketFactory != null) return;

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
        this.sslSocketFactory = sslContext.getSocketFactory();
        log.info("[Tor-mTLS] SSL Context initialized (TLS 1.3)");
    }

    /**
     * Executes an mTLS POST request over Tor.
     */
    public QuorumResponse post(String onionUrl, String txHash, String payload) throws IOException {
        try {
            ensureSslInitialized();

            java.net.URI uri = java.net.URI.create(onionUrl);
            String host = uri.getHost();
            int port = uri.getPort() == -1 ? 443 : uri.getPort();
            String path = uri.getRawPath();

            log.debug("[Tor-mTLS] Connecting to {} via Tor SOCKS UDS...", onionUrl);

            // 1. Establish SOCKS5 Tunnel over UDS
            try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
                channel.connect(UnixDomainSocketAddress.of(torSocksPath));

                performSocks5Handshake(channel, host, port);

                // 2. Wrap Socket in TLS
                try (java.net.Socket rawSocket = channel.socket();
                     java.net.Socket sslSocket = sslSocketFactory.createSocket(rawSocket, host, port, true)) {

                    ((SSLSocket) sslSocket).startHandshake();
                    log.debug("[Tor-mTLS] mTLS Handshake successful with {}", host);

                    // 3. Send HTTP Request
                    PrintWriter writer = new PrintWriter(new OutputStreamWriter(sslSocket.getOutputStream(), StandardCharsets.UTF_8));
                    writer.print("POST " + path + " HTTP/1.1\r\n");
                    writer.print("Host: " + host + "\r\n");
                    writer.print("Connection: close\r\n");
                    writer.print("Content-Type: application/json\r\n");
                    writer.print("X-Tx-Hash: " + txHash + "\r\n");
                    writer.print("Content-Length: " + payload.length() + "\r\n");
                    writer.print("\r\n");
                    writer.print(payload);
                    writer.flush();

                    // 4. Read Response
                    BufferedReader reader = new BufferedReader(new InputStreamReader(sslSocket.getInputStream(), StandardCharsets.UTF_8));
                    String line = reader.readLine();
                    if (line == null) throw new IOException("Remote shard closed connection");

                    int statusCode = Integer.parseInt(line.split(" ")[1]);
                    return new QuorumResponse(statusCode);
                }
            }
        } catch (Exception e) {
            log.error("[Tor-mTLS] Request to {} failed: {}", onionUrl, e.getMessage());
            throw new IOException("Tor mTLS failure: " + e.getMessage(), e);
        }
    }

    private void performSocks5Handshake(SocketChannel channel, String host, int port) throws IOException {
        // VER=5, NMETHODS=1, METHOD=0x00
        ByteBuffer buf = ByteBuffer.wrap(new byte[]{0x05, 0x01, 0x00});
        while (buf.hasRemaining()) channel.write(buf);

        buf = ByteBuffer.allocate(2);
        channel.read(buf);
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
        channel.read(buf);
        if (buf.get(1) != 0x00) throw new IOException("SOCKS5 Connect failed, code: " + buf.get(1));
    }

    public record QuorumResponse(int statusCode) {}
}
