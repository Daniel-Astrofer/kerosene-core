package com.kerosene.auth.application.infra.kfe;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Shared transport and credential support for Core-to-KFE HTTP adapters.
 * It constructs a bounded {@link RestTemplate}, normalizes the base URL, and adds the internal
 * shared-secret header only after verifying that the credential is configured.
 */
abstract class KfeRemoteClientSupport {

    /** Default in-cluster KFE endpoint used when no base URL is configured. */
    private static final String DEFAULT_BASE_URL = "http://kfe-service:8080";
    /** Internal authentication header expected by KFE service endpoints. */
    private static final String INTERNAL_HEADER = "X-KFE-Internal-Secret";

    /** HTTP client configured with the adapter's connect and read timeout values. */
    protected final RestTemplate restTemplate;
    /** Normalized service root without trailing slash. */
    protected final String baseUrl;
    /** Shared credential attached to outbound internal calls; never included in logs. */
    private final String internalSecret;

    /**
     * Builds the shared remote client transport and normalizes an optional service URL.
     *
     * @param restTemplateBuilder Spring builder used to construct the HTTP client
     * @param baseUrl configured KFE root URL, defaulting when absent/blank
     * @param internalSecret credential sent to KFE's internal endpoints
     * @param connectTimeoutMs maximum connection-establishment duration
     * @param readTimeoutMs maximum response-read duration
     */
    protected KfeRemoteClientSupport(
            RestTemplateBuilder restTemplateBuilder,
            String baseUrl,
            String internalSecret,
            long connectTimeoutMs,
            long readTimeoutMs) {
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .readTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.internalSecret = internalSecret;
    }

    /**
     * Creates an HTTP entity with JSON content type and authenticated internal headers.
     *
     * @param body request object serialized by the configured RestTemplate converters
     * @param <T> body type
     * @return entity containing body and internal credential headers
     * @throws IllegalStateException if the shared internal credential is absent
     */
    protected <T> HttpEntity<T> internalJsonEntity(T body) {
        HttpHeaders headers = internalHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    /**
     * Creates a bodyless authenticated request entity for internal calls.
     *
     * @return entity carrying the internal credential header
     * @throws IllegalStateException if the shared internal credential is absent
     */
    protected HttpEntity<Void> internalEntity() {
        return new HttpEntity<>(internalHeaders());
    }

    /**
     * Fails before an outbound internal request when the required shared secret is unconfigured.
     *
     * @throws IllegalStateException when Core-to-KFE authentication cannot be supplied
     */
    protected void requireInternalCredential() {
        if (internalSecret == null || internalSecret.isBlank()) {
            throw new IllegalStateException("kfe.internal.shared-secret must be configured for Core to KFE calls");
        }
    }

    /**
     * Creates request headers after validating configuration and attaches the shared-secret header.
     *
     * @return new headers containing the internal credential
     * @throws IllegalStateException if the credential is not configured
     */
    private HttpHeaders internalHeaders() {
        requireInternalCredential();
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_HEADER, internalSecret);
        return headers;
    }

    /**
     * Uses the default KFE URL for blank configuration and removes one trailing slash otherwise.
     *
     * @param value configured service root
     * @return normalized root URL without a final slash
     */
    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_BASE_URL;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
