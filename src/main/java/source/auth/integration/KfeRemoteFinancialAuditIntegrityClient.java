package source.auth.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import source.common.financial.FinancialAuditIntegrityPort;

@Component
@Profile("!kfe")
@ConditionalOnProperty(name = "kfe.remote.audit-integrity.enabled", havingValue = "true", matchIfMissing = true)
public class KfeRemoteFinancialAuditIntegrityClient extends KfeRemoteClientSupport implements FinancialAuditIntegrityPort {

    public KfeRemoteFinancialAuditIntegrityClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${kfe.remote.base-url:http://kfe-service:8080}") String baseUrl,
            @Value("${kfe.internal.shared-secret:}") String internalSecret,
            @Value("${kfe.remote.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${kfe.remote.read-timeout-ms:5000}") long readTimeoutMs) {
        super(restTemplateBuilder, baseUrl, internalSecret, connectTimeoutMs, readTimeoutMs);
    }

    @Override
    public AuditRoot root() {
        ResponseEntity<AuditRoot> response = restTemplate.exchange(
                baseUrl + "/internal/kfe/audit-integrity/root",
                HttpMethod.GET,
                internalEntity(),
                AuditRoot.class);
        return response.getBody();
    }
}
