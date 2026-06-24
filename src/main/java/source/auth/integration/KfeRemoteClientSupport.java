package source.auth.integration;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

abstract class KfeRemoteClientSupport {

    private static final String DEFAULT_BASE_URL = "http://kfe-service:8080";
    private static final String INTERNAL_HEADER = "X-KFE-Internal-Secret";

    protected final RestTemplate restTemplate;
    protected final String baseUrl;
    private final String internalSecret;

    protected KfeRemoteClientSupport(
            RestTemplateBuilder restTemplateBuilder,
            String baseUrl,
            String internalSecret,
            long connectTimeoutMs,
            long readTimeoutMs) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.internalSecret = internalSecret;
    }

    protected <T> HttpEntity<T> internalJsonEntity(T body) {
        HttpHeaders headers = internalHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    protected HttpEntity<Void> internalEntity() {
        return new HttpEntity<>(internalHeaders());
    }

    protected void requireInternalCredential() {
        if (internalSecret == null || internalSecret.isBlank()) {
            throw new IllegalStateException("kfe.internal.shared-secret must be configured for Core to KFE calls");
        }
    }

    private HttpHeaders internalHeaders() {
        requireInternalCredential();
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_HEADER, internalSecret);
        return headers;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_BASE_URL;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
