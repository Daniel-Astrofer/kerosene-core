package com.kerosene.common.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Collections;

/**
 * Proxies /kfe/** requests to kfe-service with internal auth.
 *
 * The auth server validates the JWT (Spring Security filter chain),
 * then forwards the request to KFE with X-KFE-Internal-Secret.
 */
@RestController
@RequestMapping("/kfe")
public class KfeGatewayController {

    private static final Logger log = LoggerFactory.getLogger(KfeGatewayController.class);
    private final RestTemplate restTemplate;
    private final String kfeBaseUrl;
    private final String internalSecret;

    public KfeGatewayController(
            @Value("${kfe.internal.base-url:http://kfe-service:8080}") String kfeBaseUrl,
            @Value("${kfe.internal.shared-secret:local-kfe-internal-secret-not-for-production}") String internalSecret,
            @Value("${kfe.gateway.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${kfe.gateway.read-timeout-ms:30000}") int readTimeoutMs) {
        this.kfeBaseUrl = kfeBaseUrl;
        this.internalSecret = internalSecret;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restTemplate = new RestTemplate(factory);
    }

    @RequestMapping("/**")
    public ResponseEntity<String> proxy(HttpServletRequest request) {
        String path = request.getRequestURI();
        String query = request.getQueryString();
        String targetUrl = kfeBaseUrl + path + (query != null ? "?" + query : "");

        try {
            HttpMethod method = HttpMethod.valueOf(request.getMethod());

            HttpHeaders forwardHeaders = new HttpHeaders();
            forwardHeaders.set("X-KFE-Internal-Secret", internalSecret);
            forwardHeaders.setContentType(MediaType.APPLICATION_JSON);

            // Copy client headers including Authorization (KFE validates JWT)
            for (String name : Collections.list(request.getHeaderNames())) {
                String lower = name.toLowerCase();
                if (lower.equals("host")) continue;
                String value = request.getHeader(name);
                if (value != null && !value.isEmpty()) {
                    forwardHeaders.set(name, value);
                }
            }

            byte[] body = request.getInputStream().readAllBytes();
            HttpEntity<byte[]> entity = new HttpEntity<>(body.length > 0 ? body : null, forwardHeaders);

            ResponseEntity<String> kfeResponse = restTemplate.exchange(
                    targetUrl, method, entity, String.class);

            return ResponseEntity.status(kfeResponse.getStatusCode())
                    .headers(kfeResponse.getHeaders())
                    .body(kfeResponse.getBody());

        } catch (Exception e) {
            boolean isTimeout = e instanceof org.springframework.web.client.ResourceAccessException
                    && e.getCause() instanceof SocketTimeoutException;
            HttpStatus status = isTimeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY;
            String errorCode = isTimeout ? "SYS_504" : "SYS_502";
            log.error("[KFE Gateway] {} {} failed ({}): {}", request.getMethod(), targetUrl, errorCode, e.getMessage());
            return ResponseEntity.status(status)
                    .body("{\"success\":false,\"message\":\"KFE gateway error\",\"errorCode\":\"" + errorCode + "\"}");
        }
    }
}
