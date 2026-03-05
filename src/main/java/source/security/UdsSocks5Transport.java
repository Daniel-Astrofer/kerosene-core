package source.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * UDS-native SOCKS5 transport for routing Java HTTP requests through Tor.
 *
 * ─── Why This Exists ─────────────────────────────────────────────────────────
 * Java's java.net.Proxy(Proxy.Type.SOCKS, ...) only accepts InetSocketAddress,
 * making it impossible to use Tor's Unix Domain Socket (UDS) SOCKS5 endpoint
 * via the standard HttpClient proxy API.
 *
 * Opening a TCP port (e.g., 9050) as a workaround is a security regression:
 * - Eliminates filesystem-level ACL protection of the UDS socket.
 * - Allows any process in the container (or a compromised one) to route
 * traffic through Tor without file-permission checks.
 * - Java's SOCKS5 via TCP may trigger local DNS resolution of .onion names
 * before they reach the proxy (DNS leak).
 *
 * ─── How This Works ──────────────────────────────────────────────────────────
 * 1. Opens a java.nio SocketChannel using StandardProtocolFamily.UNIX (Java
 * 16+).
 * 2. Connects directly to the Tor UDS socket file path.
 * 3. Performs a SOCKS5 handshake using no-auth (0x00).
 * 4. Issues a SOCKS5 CONNECT command with ATYP=0x03 (DOMAINNAME) so the
 * .onion hostname is NEVER resolved locally — Tor resolves it internally.
 * 5. Reads/writes the HTTP request/response directly over the established
 * tunnel.
 *
 * ─── DNS Leak Prevention ─────────────────────────────────────────────────────
 * By using ATYP_DOMAINNAME (0x03) in the SOCKS5 CONNECT request, the .onion
 * hostname is forwarded to the Tor daemon as-is. No local DNS resolution is
 * ever attempted. This is the only correct approach for .onion destinations.
 *
 * ─── Thread Safety ───────────────────────────────────────────────────────────
 * Each call to executeHttpRequest() opens and closes its own SocketChannel.
 * This class is stateless and safe to share across threads.
 */
public class UdsSocks5Transport {

    private static final Logger logger = LoggerFactory.getLogger(UdsSocks5Transport.class);

    // SOCKS5 protocol constants
    private static final byte SOCKS5_VERSION = 0x05;
    private static final byte SOCKS5_NO_AUTH = 0x00;
    private static final byte SOCKS5_CMD_CONNECT = 0x01;
    private static final byte SOCKS5_RESERVED = 0x00;
    private static final byte SOCKS5_ATYP_DOMAINNAME = 0x03;
    private static final byte SOCKS5_REPLY_SUCCESS = 0x00;

    private final String udsPath;

    public UdsSocks5Transport(String udsPath) {
        this.udsPath = udsPath;
    }

    /**
     * Executes a raw HTTP/1.1 POST request through the Tor UDS SOCKS5 tunnel.
     *
     * @param targetUrl    The full HTTP URL (http://xxx.onion/path)
     * @param method       HTTP method (GET, POST)
     * @param requestBody  Optional request body (for POST)
     * @param extraHeaders Extra headers to include (e.g., Authorization)
     * @return HttpResult containing the status code and response body as bytes
     */
    public HttpResult executeHttpRequest(
            String targetUrl,
            String method,
            String requestBody,
            Map<String, String> extraHeaders) throws IOException {

        // Parse host, port, path from the URL
        UrlComponents url = parseUrl(targetUrl);

        logger.debug("[UdsSocks5] Opening UDS channel to Tor at: {}", udsPath);

        // Step 1: Open UDS SocketChannel — Java 16+ native UDS support
        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(UnixDomainSocketAddress.of(udsPath));

            // Step 2: SOCKS5 greeting — offer no-auth (0x00)
            performSocks5Greeting(channel);

            // Step 3: SOCKS5 CONNECT — using DOMAINNAME to prevent DNS leaks
            performSocks5Connect(channel, url.host, url.port);

            logger.debug("[UdsSocks5] SOCKS5 tunnel established to {}:{}", url.host, url.port);

            // Step 4: Build and send HTTP/1.1 request
            String httpRequest = buildHttpRequest(method, url, requestBody, extraHeaders);
            byte[] requestBytes = httpRequest.getBytes(StandardCharsets.UTF_8);
            ByteBuffer reqBuf = ByteBuffer.wrap(requestBytes);
            while (reqBuf.hasRemaining()) {
                channel.write(reqBuf);
            }

            // Step 5: Read HTTP response
            return readHttpResponse(channel);
        }
    }

    // ── SOCKS5 Protocol Implementation ────────────────────────────────────────

    /**
     * Step 1: SOCKS5 greeting.
     * Sends: VER=5, NMETHODS=1, METHOD=0x00 (no auth)
     * Expects: VER=5, METHOD=0x00
     */
    private void performSocks5Greeting(SocketChannel channel) throws IOException {
        // Send: [0x05, 0x01, 0x00] => version 5, 1 method, no-auth
        byte[] greeting = { SOCKS5_VERSION, 0x01, SOCKS5_NO_AUTH };
        writeAll(channel, ByteBuffer.wrap(greeting));

        // Read server choice: [VER, METHOD]
        ByteBuffer response = readExactly(channel, 2);
        byte ver = response.get();
        byte method = response.get();

        if (ver != SOCKS5_VERSION) {
            throw new IOException("[UdsSocks5] SOCKS5 server responded with unexpected version: " + ver);
        }
        if (method != SOCKS5_NO_AUTH) {
            throw new IOException("[UdsSocks5] SOCKS5 server rejected no-auth method. Responded: " + method);
        }
    }

    /**
     * Step 2: SOCKS5 CONNECT using ATYP=DOMAINNAME.
     * This ensures the .onion hostname is NEVER resolved locally.
     *
     * Request format:
     * [VER=5][CMD=1][RSV=0][ATYP=3][DOMAIN_LEN][DOMAIN_BYTES...][PORT_HI][PORT_LO]
     */
    private void performSocks5Connect(SocketChannel channel, String host, int port) throws IOException {
        byte[] hostBytes = host.getBytes(StandardCharsets.US_ASCII);
        if (hostBytes.length > 255) {
            throw new IOException("[UdsSocks5] Hostname too long for SOCKS5 DOMAINNAME: " + host);
        }

        // Construct CONNECT request
        ByteBuffer req = ByteBuffer.allocate(4 + 1 + hostBytes.length + 2);
        req.put(SOCKS5_VERSION); // VER
        req.put(SOCKS5_CMD_CONNECT); // CMD = CONNECT
        req.put(SOCKS5_RESERVED); // RSV
        req.put(SOCKS5_ATYP_DOMAINNAME);// ATYP = domain name
        req.put((byte) hostBytes.length);// Length of domain
        req.put(hostBytes); // Domain name bytes
        req.put((byte) ((port >> 8) & 0xFF)); // Port high byte
        req.put((byte) (port & 0xFF)); // Port low byte
        req.flip();
        writeAll(channel, req);

        // Read server response: [VER][REP][RSV][ATYP][BADDR...][BPORT]
        // Minimum 10 bytes for IPv4 response
        ByteBuffer replyHeader = readExactly(channel, 4);
        byte repVer = replyHeader.get();
        byte repCode = replyHeader.get();
        replyHeader.get(); // RSV — skip
        byte atyp = replyHeader.get();

        if (repVer != SOCKS5_VERSION) {
            throw new IOException("[UdsSocks5] SOCKS5 CONNECT reply has invalid version: " + repVer);
        }
        if (repCode != SOCKS5_REPLY_SUCCESS) {
            throw new IOException(
                    "[UdsSocks5] SOCKS5 CONNECT failed with code: 0x" + Integer.toHexString(repCode & 0xFF));
        }

        // Consume the bound address from the reply (we don't use it)
        // ATYP 0x01 = IPv4 (4 bytes), 0x03 = domain (1+N bytes), 0x04 = IPv6 (16 bytes)
        int addrLen = switch (atyp) {
            case 0x01 -> 4;
            case 0x04 -> 16;
            case 0x03 -> {
                ByteBuffer lenBuf = readExactly(channel, 1);
                yield lenBuf.get() & 0xFF;
            }
            default -> throw new IOException("[UdsSocks5] Unknown ATYP in CONNECT reply: " + atyp);
        };
        // Read remaining address bytes + 2 port bytes
        readExactly(channel, addrLen + 2);

        logger.debug("[UdsSocks5] SOCKS5 CONNECT success. Tunnel is active.");
    }

    // ── HTTP/1.1 I/O ─────────────────────────────────────────────────────────

    private String buildHttpRequest(
            String method,
            UrlComponents url,
            String requestBody,
            Map<String, String> extraHeaders) {

        StringBuilder sb = new StringBuilder();
        // Use HTTP/1.0 to prevent Chunked Transfer Encoding in the response
        sb.append(method).append(' ').append(url.path).append(" HTTP/1.0\r\n");
        sb.append("Host: ").append(url.host);
        if (url.port != 80 && url.port != 443)
            sb.append(':').append(url.port);
        sb.append("\r\n");
        sb.append("Connection: close\r\n");
        sb.append("User-Agent: Kerosene-Shard/1.0\r\n");

        if (extraHeaders != null) {
            extraHeaders.forEach((k, v) -> sb.append(k).append(": ").append(v).append("\r\n"));
        }

        if (requestBody != null && !requestBody.isEmpty()) {
            byte[] bodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);
            sb.append("Content-Type: application/json\r\n");
            sb.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
            sb.append("\r\n");
            sb.append(requestBody);
        } else {
            sb.append("Content-Length: 0\r\n");
            sb.append("\r\n");
        }

        return sb.toString();
    }

    /**
     * Reads an HTTP/1.1 response from the channel.
     * Handles both Content-Length and Connection: close chunking patterns.
     */
    private HttpResult readHttpResponse(SocketChannel channel) throws IOException {
        // Read all bytes until connection close
        var accumulator = new java.io.ByteArrayOutputStream();
        ByteBuffer buf = ByteBuffer.allocate(8192);
        while (true) {
            buf.clear();
            int n = channel.read(buf);
            if (n < 0)
                break;
            buf.flip();
            byte[] chunk = new byte[buf.remaining()];
            buf.get(chunk);
            accumulator.write(chunk);
        }

        byte[] rawResponse = accumulator.toByteArray();
        String responseStr = new String(rawResponse, StandardCharsets.UTF_8);

        // Parse status line: "HTTP/1.1 200 OK\r\n..."
        int firstLineEnd = responseStr.indexOf("\r\n");
        if (firstLineEnd < 0) {
            throw new IOException("[UdsSocks5] Malformed HTTP response: no CRLF in first 8192 bytes.");
        }
        String statusLine = responseStr.substring(0, firstLineEnd);
        String[] parts = statusLine.split(" ", 3);
        if (parts.length < 2) {
            throw new IOException("[UdsSocks5] Cannot parse HTTP status line: " + statusLine);
        }
        int statusCode;
        try {
            statusCode = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new IOException("[UdsSocks5] Non-numeric HTTP status: " + parts[1]);
        }

        // Split header/body at the blank line
        int headerEnd = responseStr.indexOf("\r\n\r\n");
        byte[] body = headerEnd >= 0
                ? responseStr.substring(headerEnd + 4).getBytes(StandardCharsets.UTF_8)
                : new byte[0];

        return new HttpResult(statusCode, body);
    }

    // ── NIO Helpers ───────────────────────────────────────────────────────────

    private void writeAll(SocketChannel channel, ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            channel.write(buf);
        }
    }

    private ByteBuffer readExactly(SocketChannel channel, int n) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(n);
        while (buf.hasRemaining()) {
            int read = channel.read(buf);
            if (read < 0) {
                throw new IOException("[UdsSocks5] Unexpected EOF while reading " + n + " bytes from SOCKS5 server.");
            }
        }
        buf.flip();
        return buf;
    }

    // ── URL Parsing ───────────────────────────────────────────────────────────

    private UrlComponents parseUrl(String url) throws IOException {
        try {
            java.net.URI uri = java.net.URI.create(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (host == null) {
                throw new IOException("[UdsSocks5] Target URL has no host: " + url);
            }
            int port = uri.getPort();
            if (port == -1)
                port = "https".equalsIgnoreCase(scheme) ? 443 : 80;
            String path = uri.getRawPath();
            if (path == null || path.isEmpty())
                path = "/";
            String query = uri.getRawQuery();
            if (query != null && !query.isEmpty())
                path += "?" + query;
            return new UrlComponents(host, port, path);
        } catch (Exception e) {
            throw new IOException("[UdsSocks5] Could not parse target URL: " + url, e);
        }
    }

    // ── Value Types ───────────────────────────────────────────────────────────

    private record UrlComponents(String host, int port, String path) {
    }

    /**
     * Result of an HTTP request through the SOCKS5 tunnel.
     */
    public record HttpResult(int statusCode, byte[] body) {
        public String bodyAsString() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }
}
