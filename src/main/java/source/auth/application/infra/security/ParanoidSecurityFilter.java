package source.auth.application.infra.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ReadListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import source.security.SuicideService;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.nio.charset.StandardCharsets;

/**
 * Filtro de Entrada "Blind" e Camada Paranoica
 * Responsável por Noise Injection (Tempo Constante e Padding),
 * Rejeição de Headers sujos, e Content-Type Strictness.
 */
@Component
public class ParanoidSecurityFilter extends OncePerRequestFilter {

    private final SecureRandom secureRandom = new SecureRandom();
    private final SuicideService suicideService;
    private final boolean constantTimePaddingEnabled;
    private final long constantTimeTargetMs;

    @Autowired
    public ParanoidSecurityFilter(
            SuicideService suicideService,
            @Value("${security.constant-time-padding.enabled:false}") boolean constantTimePaddingEnabled,
            @Value("${security.constant-time-padding.target-ms:250}") long constantTimeTargetMs) {
        this.suicideService = suicideService;
        this.constantTimePaddingEnabled = constantTimePaddingEnabled;
        this.constantTimeTargetMs = Math.max(0, constantTimeTargetMs);
    }

    public ParanoidSecurityFilter(SuicideService suicideService) {
        this(suicideService, false, 250);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. O Filtro de Entrada "Blind"
        String contentType = request.getContentType();
        boolean hasBody = request.getContentLength() > 0 || request.getHeader("Transfer-Encoding") != null;

        if (hasBody) {
            // Strict Content-Type
            if (contentType == null || (!contentType.startsWith("application/json")
                    && !contentType.startsWith("application/x-protobuf"))) {
                response.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
                return; // Rejeita e encerra silenciosamente
            }

            // Payload Limit Guard: default 2KB. PSBT payloads are larger by
            // design, so only the PSBT routes get a bounded 64KB envelope.
            int maxPayloadBytes = maxPayloadBytesForPath(request.getRequestURI());
            if (request.getContentLength() > maxPayloadBytes) {
                response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                return;
            }
        }

        // Sanitização de Headers Forjáveis (Limpa X-Forwarded-For, Via, User-Agent)
        HttpServletRequest sanitizedRequest = new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                if (isBannedHeader(name))
                    return null;
                return super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                if (isBannedHeader(name))
                    return Collections.emptyEnumeration();
                return super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                List<String> names = Collections.list(super.getHeaderNames());
                names.removeIf(this::isBannedHeader);
                return Collections.enumeration(names);
            }

            private boolean isBannedHeader(String name) {
                String lower = name.toLowerCase();
                return lower.equals("x-forwarded-for") || lower.equals("via") || lower.equals("user-agent");
            }
        };

        byte[] bodyBytes = readRequestBodyIfPresent(sanitizedRequest, hasBody);
        if (bodyBytes.length > maxPayloadBytesForPath(request.getRequestURI())) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }

        if (!verifyMilitaryDigest(sanitizedRequest, bodyBytes)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        HttpServletRequest requestForChain = hasBody
                ? new CachedBodyRequestWrapper(sanitizedRequest, bodyBytes)
                : sanitizedRequest;

        // 2. Response: O "Manto de Silêncio"
        response.setHeader("X-Content-Type-Options", "nosniff");
        // Força HSTS para bloquear forever downgrades HTTP
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
        // Apaga rastros
        response.setHeader("Server", "");
        response.setHeader("X-Powered-By", "");
        response.setHeader("Date", "");

        // Timer para Constant Time Responses
        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(requestForChain, response);
        } finally {
            // 3. Camada Paranoica: Noise Injection

            // A) Response Padding
            // Insere de 64 a 256 bytes de lixo base64 para que pacotes HTTP TLS nunca
            // tenham o mesmo tamanho
            byte[] padding = new byte[secureRandom.nextInt(64, 256)];
            secureRandom.nextBytes(padding);
            response.setHeader("X-Pad-Noise", Base64.getEncoder().encodeToString(padding));

            applyConstantTimePadding(request.getRequestURI(), startTime);
        }
    }

    private void applyConstantTimePadding(String path, long startTime) {
        if (!constantTimePaddingEnabled) {
            return;
        }
        if (!(path.contains("/auth/") || path.contains("/ledger/"))) {
            return;
        }

        long duration = System.currentTimeMillis() - startTime;
        if (duration >= constantTimeTargetMs) {
            return;
        }

        try {
            Thread.sleep(constantTimeTargetMs - duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private int maxPayloadBytesForPath(String path) {
        if (path != null && (path.startsWith("/bitcoin/psbt/")
                || path.contains("/cold-wallets/") && path.endsWith("/psbt"))) {
            return 64 * 1024;
        }
        return 2048;
    }

    /**
     * Valida o Header de Digest = SHA-256
     * Se houver divergência de bit, assume interceptação Middle-Man / Taint na RAM
     * e suicida.
     */
    private byte[] readRequestBodyIfPresent(HttpServletRequest request, boolean hasBody) throws IOException {
        if (!hasBody) {
            return new byte[0];
        }
        return request.getInputStream().readAllBytes();
    }

    private boolean verifyMilitaryDigest(HttpServletRequest request, byte[] bodyBytes) {
        String digestHeader = request.getHeader("Digest");
        if (digestHeader != null && digestHeader.startsWith("SHA-256=")) {
            String clientHash = digestHeader.substring(8);

            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] serverHashBytes = md.digest(bodyBytes);
                String serverHash = Base64.getEncoder().encodeToString(serverHashBytes);

                if (!MessageDigest.isEqual(clientHash.getBytes(), serverHash.getBytes())) {
                    suicideService.triggerInstantSuicide(
                            "Payload Digest Hash Mismatch! Request Tampered or Man-In-The-Middle. Client sent "
                                    + clientHash + " but calculated " + serverHash);
                    return false;
                }
            } catch (NoSuchAlgorithmException e) {
                // Ignorado (Impossível na JVM Java padrão)
            }
        }
        return true;
    }

    private static final class CachedBodyRequestWrapper extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body != null ? body : new byte[0];
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    if (readListener == null) {
                        return;
                    }
                    try {
                        readListener.onDataAvailable();
                        if (isFinished()) {
                            readListener.onAllDataRead();
                        }
                    } catch (IOException e) {
                        readListener.onError(e);
                    }
                }

                @Override
                public int read() {
                    return input.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
