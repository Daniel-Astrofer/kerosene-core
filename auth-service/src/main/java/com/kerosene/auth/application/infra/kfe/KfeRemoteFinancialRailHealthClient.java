package com.kerosene.auth.application.infra.kfe;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import com.kerosene.common.financial.operations.FinancialRailHealthPort;

import java.util.List;

/** Fetches internal custody and external-provider health projections from KFE. */
@Component
@Profile("!kfe")
@ConditionalOnProperty(name = "kfe.remote.rail-health.enabled", havingValue = "true", matchIfMissing = true)
public class KfeRemoteFinancialRailHealthClient extends KfeRemoteClientSupport implements FinancialRailHealthPort {

    /** Captured generic response type for the provider health list. */
    private static final ParameterizedTypeReference<List<ProviderHealth>> PROVIDER_LIST_TYPE =
            new ParameterizedTypeReference<List<ProviderHealth>>() {};

    /**
     * Creates the client with shared KFE transport settings and internal authentication.
     *
     * @param restTemplateBuilder Spring HTTP client builder
     * @param baseUrl KFE service root
     * @param internalSecret credential for internal health endpoints
     * @param connectTimeoutMs connection timeout
     * @param readTimeoutMs response timeout
     */
    public KfeRemoteFinancialRailHealthClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${kfe.remote.base-url:http://kfe-service:8080}") String baseUrl,
            @Value("${kfe.internal.shared-secret:}") String internalSecret,
            @Value("${kfe.remote.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${kfe.remote.read-timeout-ms:5000}") long readTimeoutMs) {
        super(restTemplateBuilder, baseUrl, internalSecret, connectTimeoutMs, readTimeoutMs);
    }

    /**
     * Retrieves the configured custody provider health snapshot.
     *
     * @return custody provider health, or null when the successful response has no body
     */
    @Override
    public ProviderHealth custodyProviderHealth() {
        ResponseEntity<ProviderHealth> response = restTemplate.exchange(
                baseUrl + "/internal/kfe/rail-health/custody-provider",
                HttpMethod.GET,
                internalEntity(),
                ProviderHealth.class);
        return response.getBody();
    }

    /**
     * Retrieves health status for all configured external financial rail providers.
     *
     * @return provider list, normalized to empty when the successful response has no body
     */
    @Override
    public List<ProviderHealth> activeRailProviderHealth() {
        ResponseEntity<List<ProviderHealth>> response = restTemplate.exchange(
                baseUrl + "/internal/kfe/rail-health/external-providers",
                HttpMethod.GET,
                internalEntity(),
                PROVIDER_LIST_TYPE);
        List<ProviderHealth> body = response.getBody();
        return body != null ? body : List.of();
    }
}
