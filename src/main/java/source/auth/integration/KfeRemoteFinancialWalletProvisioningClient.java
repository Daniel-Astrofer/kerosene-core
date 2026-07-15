package source.auth.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import source.common.financial.FinancialWalletProvisioningPort;
import source.common.financial.FinancialWalletProvisioningRequest;
import source.common.infra.logging.LogSanitizer;

@Component
@Profile("!kfe")
@ConditionalOnProperty(name = "kfe.remote.wallet-provisioning.enabled", havingValue = "true", matchIfMissing = true)
public class KfeRemoteFinancialWalletProvisioningClient extends KfeRemoteClientSupport implements FinancialWalletProvisioningPort {

    private static final Logger log = LoggerFactory.getLogger(KfeRemoteFinancialWalletProvisioningClient.class);

    public KfeRemoteFinancialWalletProvisioningClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${kfe.remote.base-url:http://kfe-service:8080}") String baseUrl,
            @Value("${kfe.internal.shared-secret:}") String internalSecret,
            @Value("${kfe.remote.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${kfe.remote.read-timeout-ms:5000}") long readTimeoutMs) {
        super(restTemplateBuilder, baseUrl, internalSecret, connectTimeoutMs, readTimeoutMs);
    }

    @Override
    public void ensurePrimaryWalletReady(Long userId, String initialAddress) {
        if (userId == null) {
            return;
        }
        FinancialWalletProvisioningRequest request = new FinancialWalletProvisioningRequest(userId, initialAddress);
        try {
            restTemplate.postForEntity(
                    baseUrl + "/internal/kfe/wallet-provisioning/primary",
                    internalJsonEntity(request),
                    Void.class);
            log.info("[Onboarding] Remote KFE primary wallet ensured for userId={}", userId);
        } catch (RestClientResponseException exception) {
            log.warn(
                    "Remote KFE wallet provisioning failed for userId={} status={} bodyRef={}",
                    userId,
                    exception.getStatusCode(),
                    LogSanitizer.fingerprint(exception.getResponseBodyAsString()));
            throw exception;
        }
    }
}
