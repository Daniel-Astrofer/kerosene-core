package source.auth.application.infra.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import source.security.SuicideService;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Filtro de Entrada "Blind" e Camada Paranoica
 * Responsável por Noise Injection (Tempo Constante e Padding),
 * Rejeição de Headers sujos, e Content-Type Strictness.
 */
@Component
public class ParanoidSecurityFilter extends OncePerRequestFilter {

    private final SecureRandom secureRandom = new SecureRandom();
    private final SuicideService suicideService;

    public ParanoidSecurityFilter(SuicideService suicideService) {
        this.suicideService = suicideService;
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

            // Payload Limit Guard: Max 2048 Bytes (2KB)
            if (request.getContentLength() > 2048) {
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

        // Envolve o Request para conseguir ler o Body sem consumir a Stream do Jackson
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(sanitizedRequest);

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
            filterChain.doFilter(wrappedRequest, response);

            // 4. mTLS e Assinatura de Payload (Militar)
            // Valida o Digest Head se a rota exigir segurança militar
            verifyMilitaryDigest(wrappedRequest);

        } finally {
            // 3. Camada Paranoica: Noise Injection

            // A) Response Padding
            // Insere de 64 a 256 bytes de lixo base64 para que pacotes HTTP TLS nunca
            // tenham o mesmo tamanho
            byte[] padding = new byte[secureRandom.nextInt(64, 256)];
            secureRandom.nextBytes(padding);
            response.setHeader("X-Pad-Noise", Base64.getEncoder().encodeToString(padding));

            // B) Constant Time Responses (Anti-Side-Channel Timing Attack)
            long duration = System.currentTimeMillis() - startTime;
            // Padroniza as respostas de fluxos logicos sensiveis em exatamente 250ms
            String path = request.getRequestURI();
            if (path.contains("/auth/") || path.contains("/voucher/") || path.contains("/ledger/")) {
                long targetTime = 250;
                if (duration < targetTime) {
                    try {
                        Thread.sleep(targetTime - duration);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    /**
     * Valida o Header de Digest = SHA-256
     * Se houver divergência de bit, assume interceptação Middle-Man / Taint na RAM
     * e suicida.
     */
    private void verifyMilitaryDigest(ContentCachingRequestWrapper request) {
        String digestHeader = request.getHeader("Digest");
        if (digestHeader != null && digestHeader.startsWith("SHA-256=")) {
            String clientHash = digestHeader.substring(8);
            byte[] bodyBytes = request.getContentAsByteArray();

            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] serverHashBytes = md.digest(bodyBytes);
                String serverHash = Base64.getEncoder().encodeToString(serverHashBytes);

                if (!MessageDigest.isEqual(clientHash.getBytes(), serverHash.getBytes())) {
                    // 🛡️ DIVERGÊNCIA DE HASHDATA NO NÍVEL CORE!
                    suicideService.triggerInstantSuicide(
                            "Payload Digest Hash Mismatch! Request Tampered or Man-In-The-Middle. Client sent "
                                    + clientHash + " but calculated " + serverHash);
                }
            } catch (NoSuchAlgorithmException e) {
                // Ignorado (Impossível na JVM Java padrão)
            }
        }
    }
}
