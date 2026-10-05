package com.kerosene.auth.application.infra.kfe;

import com.kerosene.common.security.workload.InternalServiceRestTemplateFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

/**
 * Shared transport and credential support for Core-to-KFE HTTP adapters.
 * Configures the HTTP client with workload identity or local internal credentials.
 */
abstract class KfeRemoteClientSupport {

    /** Default in-cluster KFE endpoint used when no base URL is configured. */
    private static final String DEFAULT_BASE_URL = "http://kfe-service:8080";

    /** HTTP client configured with the adapter's connect and read timeout values. */
    protected final RestTemplate restTemplate;
    /** Normalized service root without trailing slash. */
    protected final String baseUrl;

    /**
     * Builds the shared remote client transport and normalizes an optional service URL.
     *
     * @param restTemplateFactory factory creating workload-identity configured RestTemplates
     * @param baseUrl configured KFE root URL, defaulting when absent/blank
     * @param connectTimeoutMs maximum connection-establishment duration
     * @param readTimeoutMs maximum response-read duration
     */
    protected KfeRemoteClientSupport(
            InternalServiceRestTemplateFactory restTemplateFactory,
            String baseUrl,
            long connectTimeoutMs,
            long readTimeoutMs) {
        InternalServiceRestTemplateFactory.ConfiguredClient client = restTemplateFactory.create(
                baseUrl,
                DEFAULT_BASE_URL,
                connectTimeoutMs,
                readTimeoutMs);
        this.restTemplate = client.restTemplate();
        this.baseUrl = client.baseUrl();
    }

    /**
     * Creates an HTTP entity with JSON content type.
     *
     * @param body request object serialized by the configured RestTemplate converters
     * @param <T> body type
     * @return entity containing body and headers
     */
    protected <T> HttpEntity<T> internalJsonEntity(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    /**
     * Creates a bodyless request entity for internal calls.
     *
     * @return entity carrying standard internal headers
     */
    protected HttpEntity<Void> internalEntity() {
        return new HttpEntity<>(new HttpHeaders());
    }
}
