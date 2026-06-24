package source.auth.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import source.common.financial.FinancialRailHealthPort;

import java.util.Map;

@Component
@Profile("!kfe")
@ConditionalOnProperty(name = "kfe.remote.rail-health.enabled", havingValue = "true", matchIfMissing = true)
public class KfeRemoteFinancialRailHealthClient extends KfeRemoteClientSupport implements FinancialRailHealthPort {

    private static final ParameterizedTypeReference<Map<String, ProviderStatus>> PROVIDER_MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    public KfeRemoteFinancialRailHealthClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${kfe.remote.base-url:http://kfe-service:8080}") String baseUrl,
            @Value("${kfe.internal.shared-secret:}") String internalSecret,
            @Value("${kfe.remote.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${kfe.remote.read-timeout-ms:5000}") long readTimeoutMs) {
        super(restTemplateBuilder, baseUrl, internalSecret, connectTimeoutMs, readTimeoutMs);
    }

    @Override
    public ProviderStatus custodyProvider() {
        ResponseEntity<ProviderStatus> response = restTemplate.exchange(
                baseUrl + "/internal/kfe/rail-health/custody-provider",
                HttpMethod.GET,
                internalEntity(),
                ProviderStatus.class);
        return response.getBody();
    }

    @Override
    public Map<String, ProviderStatus> activeRailProviders() {
        ResponseEntity<Map<String, ProviderStatus>> response = restTemplate.exchange(
                baseUrl + "/internal/kfe/rail-health/external-providers",
                HttpMethod.GET,
                internalEntity(),
                PROVIDER_MAP_TYPE);
        Map<String, ProviderStatus> body = response.getBody();
        return body != null ? body : Map.of();
    }
}
