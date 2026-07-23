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

    private static final int DEFAULT_TIMEOUT_MS = 30000; // Increased for Tor circuits
 // 10 seconds

    private final String udsPath;
    private final int timeoutMs;

    public UdsSocks5Transport(String udsPath) {
        this(udsPath, DEFAULT_TIMEOUT_MS);
    }

    public UdsSocks5Transport(String udsPath, int timeoutMs) {
        this.udsPath = udsPath;
        this.timeoutMs = timeoutMs;
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
            // Set non-blocking mode to enable Selector-based timeouts
            channel.configureBlocking(false);
            
            // Connect with timeout
            if (!channel.connect(UnixDomainSocketAddress.of(udsPath))) {
                try (java.nio.channels.Selector selector = java.nio.channels.Selector.open()) {
                    channel.register(selector, java.nio.channels.SelectionKey.OP_CONNECT);
                    if (selector.select(timeoutMs) == 0) {
                        throw new IOException("[UdsSocks5] Connection timeout to Tor at: " + udsPath);
                    }
                    if (!channel.finishConnect()) {
                        throw new IOException("[UdsSocks5] Failed to finish connection to: " + udsPath);
                    }
                }
            }

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
        ByteBuffer response = readExactly(channel, 2, "Greeting response");
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
        ByteBuffer replyHeader = readExactly(channel, 4, "CONNECT reply header");
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
                ByteBuffer lenBuf = readExactly(channel, 1, "CONNECT domain length");
                yield lenBuf.get() & 0xFF;
            }
            default -> throw new IOException("[UdsSocks5] Unknown ATYP in CONNECT reply: " + atyp);
        };
        // Read remaining address bytes + 2 port bytes
        readExactly(channel, addrLen + 2, "CONNECT bound address/port");

        logger.debug("[UdsSocks5] SOCKS5 CONNECT success. Tunnel is active.");
    }

    // ── HTTP/1.1 I/O ─────────────────────────────────────────────────────────

    private String buildHttpRequest(
            String method,
            UrlComponents url,
            String requestBody,
            Map<String, String> extraHeaders) {

        StringBuilder sb = new StringBuilder();
        // Use HTTP/1.1
        sb.append(method).append(' ').append(url.path).append(" HTTP/1.1\r\n");
        sb.append("Host: ").append(url.host);
        if (url.port != 80 && url.port != 443)
            sb.append(':').append(url.port);
        sb.append("\r\n");
        // Don't close immediately to allow Content-Length sensing if possible, 
        // though our current implementation closes anyway.
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
     * Reads an HTTP response from the channel.
     * Robust implementation that handles:
     * 1. Header parsing to find Content-Length.
     * 2. Reading exactly Content-Length bytes once found.
     * 3. Fallback to reading until EOF (connection close).
     */
    private HttpResult readHttpResponse(SocketChannel channel) throws IOException {
        var accumulator = new java.io.ByteArrayOutputStream();
        ByteBuffer buf = ByteBuffer.allocate(4096);
        boolean headersComplete = false;
        boolean isChunked = false;
        int statusCode = -1;
        long contentLength = -1;
        int headerLength = -1;

        try (java.nio.channels.Selector selector = java.nio.channels.Selector.open()) {
            channel.register(selector, java.nio.channels.SelectionKey.OP_READ);

            // 1. Read Headers
            while (!headersComplete) {
                if (selector.select(timeoutMs) == 0) {
                    throw new IOException("[UdsSocks5] Read timeout waiting for HTTP headers after " + timeoutMs + "ms");
                }
                
                buf.clear();
                int n = channel.read(buf);
                if (n < 0) break;
                if (n == 0) continue;

                buf.flip();
                byte[] chunk = new byte[buf.remaining()];
                buf.get(chunk);
                accumulator.write(chunk);
                selector.selectedKeys().clear();

                // Check for double CRLF
                byte[] currentData = accumulator.toByteArray();
                String currentStr = new String(currentData, StandardCharsets.UTF_8);
                int headerEndIndex = currentStr.indexOf("\r\n\r\n");
                if (headerEndIndex != -1) {
                    headersComplete = true;
                    headerLength = headerEndIndex + 4;
                    
                    String headersPart = currentStr.substring(0, headerEndIndex);
                    String[] lines = headersPart.split("\r\n");
                    
                    // Parse Status Line
                    if (lines.length > 0) {
                        String statusLine = lines[0];
                        String[] statusParts = statusLine.split(" ", 3);
                        if (statusParts.length >= 2) {
                            try {
                                statusCode = Integer.parseInt(statusParts[1]);
                            } catch (NumberFormatException ignored) {}
                        }
                    }

                    // Parse Content-Length and Transfer-Encoding
                    for (String line : lines) {
                        String lower = line.toLowerCase();
                        if (lower.startsWith("content-length:")) {
                            try {
                                contentLength = Long.parseLong(line.substring(15).trim());
                            } catch (NumberFormatException ignored) {}
                        } else if (lower.startsWith("transfer-encoding:") && lower.contains("chunked")) {
                            isChunked = true;
                        }
                    }
                }
            }

            if (statusCode == -1) {
                throw new IOException("[UdsSocks5] Failed to receive valid HTTP status line.");
            }

            // 2. Read Body
            byte[] decodedBody;
            if (isChunked) {
                decodedBody = decodeChunkedBody(channel, selector, accumulator, headerLength);
            } else if (contentLength >= 0) {
                decodedBody = readFixedLengthBody(channel, selector, accumulator, headerLength, contentLength);
            } else {
                decodedBody = readUntilEofBody(channel, selector, accumulator, headerLength);
            }

            return new HttpResult(statusCode, decodedBody);
        }
    }

    private byte[] decodeChunkedBody(SocketChannel channel, java.nio.channels.Selector selector, 
                                   java.io.ByteArrayOutputStream accumulator, int headerLength) throws IOException {
        var bodyOut = new java.io.ByteArrayOutputStream();
        byte[] initialData = accumulator.toByteArray();
        
        // Use a wrapper to read bytes efficiently from the already-read buffer + socket
        java.io.InputStream in = new java.io.InputStream() {
            int offset = headerLength;
            ByteBuffer buf = ByteBuffer.allocate(8192);

            @Override
            public int read() throws IOException {
                if (offset < initialData.length) {
                    return initialData[offset++] & 0xFF;
                }
                if (!buf.hasRemaining()) {
                    buf.clear();
                    if (selector.select(timeoutMs) == 0) {
                        throw new IOException("[UdsSocks5] Read timeout (chunked body) after " + timeoutMs + "ms");
                    }
                    int n = channel.read(buf);
                    if (n < 0) return -1;
                    if (n == 0) return read(); // Retry if nothing read but not EOF
                    buf.flip();
                }
                return buf.get() & 0xFF;
            }
        };

        while (true) {
            String line = readLine(in);
            if (line == null) throw new IOException("[UdsSocks5] Unexpected EOF in chunked header");
            
            // Hex size might have extras (chunk extensions)
            int firstSemi = line.indexOf(';');
            String hex = (firstSemi != -1) ? line.substring(0, firstSemi).trim() : line.trim();
            if (hex.isEmpty()) continue; // Skip empty lines if any
            
            int chunkSize = Integer.parseInt(hex, 16);
            if (chunkSize == 0) break; // Final chunk
            
            // Read chunkSize bytes
            for (int i = 0; i < chunkSize; i++) {
                int b = in.read();
                if (b == -1) throw new IOException("[UdsSocks5] Unexpected EOF in chunk data");
                bodyOut.write(b);
            }
            
            // Read trial CRLF
            readLine(in); 
        }
        return bodyOut.toByteArray();
    }

    private String readLine(java.io.InputStream in) throws IOException {
        java.io.ByteArrayOutputStream lout = new java.io.ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\r') {
                int next = in.read();
                if (next == '\n') break;
                lout.write(b);
                if (next != -1) lout.write(next);
            } else {
                lout.write(b);
            }
        }
        return lout.size() == 0 && b == -1 ? null : lout.toString(StandardCharsets.UTF_8);
    }

    private byte[] readFixedLengthBody(SocketChannel channel, java.nio.channels.Selector selector,
                                     java.io.ByteArrayOutputStream accumulator, int headerLength, long contentLength) throws IOException {
        long currentBodyLength = accumulator.size() - headerLength;
        long remainingToRead = contentLength - currentBodyLength;
        ByteBuffer buf = ByteBuffer.allocate(4096);
        
        while (remainingToRead > 0) {
            if (selector.select(timeoutMs) == 0) {
                throw new IOException("[UdsSocks5] Read timeout waiting for HTTP body after " + timeoutMs + "ms");
            }
            
            buf.clear();
            if (remainingToRead < buf.capacity()) {
                buf.limit((int) remainingToRead);
            }
            int n = channel.read(buf);
            if (n < 0) break; // Unexpected EOF
            if (n == 0) continue;

            buf.flip();
            remainingToRead -= buf.remaining();
            byte[] chunk = new byte[buf.remaining()];
            buf.get(chunk);
            accumulator.write(chunk);
            selector.selectedKeys().clear();
        }

        byte[] allBytes = accumulator.toByteArray();
        byte[] body = new byte[(int) contentLength];
        System.arraycopy(allBytes, headerLength, body, 0, body.length);
        return body;
    }

    private byte[] readUntilEofBody(SocketChannel channel, java.nio.channels.Selector selector,
                                  java.io.ByteArrayOutputStream accumulator, int headerLength) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(4096);
        while (true) {
            if (selector.select(timeoutMs) == 0) {
                throw new IOException("[UdsSocks5] Read timeout waiting for EOF after " + timeoutMs + "ms");
            }
            
            buf.clear();
            int n = channel.read(buf);
            if (n < 0) break;
            if (n == 0) continue;

            buf.flip();
            byte[] chunk = new byte[buf.remaining()];
            buf.get(chunk);
            accumulator.write(chunk);
            selector.selectedKeys().clear();
        }

        byte[] allBytes = accumulator.toByteArray();
        byte[] body = new byte[allBytes.length - headerLength];
        System.arraycopy(allBytes, headerLength, body, 0, body.length);
        return body;
    }

    // ── NIO Helpers ───────────────────────────────────────────────────────────

    private void writeAll(SocketChannel channel, ByteBuffer buf) throws IOException {
        try (java.nio.channels.Selector selector = java.nio.channels.Selector.open()) {
            channel.register(selector, java.nio.channels.SelectionKey.OP_WRITE);
            while (buf.hasRemaining()) {
                if (selector.select(timeoutMs) == 0) {
                    throw new IOException("[UdsSocks5] Write timeout after " + timeoutMs + "ms");
                }
                int n = channel.write(buf);
                if (n == 0) {
                    // This shouldn't happen if select says it's writable
                    selector.selectedKeys().clear();
                }
                selector.selectedKeys().clear();
            }
        }
    }

    private ByteBuffer readExactly(SocketChannel channel, int n, String context) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(n);
        try (java.nio.channels.Selector selector = java.nio.channels.Selector.open()) {
            channel.register(selector, java.nio.channels.SelectionKey.OP_READ);
            while (buf.hasRemaining()) {
                if (selector.select(timeoutMs) == 0) {
                    throw new IOException("[UdsSocks5] Read timeout for " + context + " after " + timeoutMs + "ms");
                }
                int read = channel.read(buf);
                if (read < 0) {
                    throw new IOException("[UdsSocks5] Unexpected EOF while reading " + n + " bytes for " + context);
                }
                selector.selectedKeys().clear();
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
