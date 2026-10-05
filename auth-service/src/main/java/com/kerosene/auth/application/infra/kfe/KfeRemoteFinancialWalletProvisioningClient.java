package com.kerosene.auth.application.infra.kfe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import com.kerosene.common.exception.FinancialProviderUnavailableException;
import com.kerosene.common.financial.operations.FinancialWalletProvisioningPort;
import com.kerosene.common.financial.operations.FinancialWalletProvisioningRequest;
import com.kerosene.common.infra.logging.LogSanitizer;

/**
 * Ensures a user's primary wallet through the authenticated remote KFE endpoint.
 * Transport and HTTP failures are converted to a stable provider-unavailable exception; logs include
 * only a fingerprint of the user identifier.
 */
@Component
@Profile("!kfe")
@ConditionalOnProperty(name = "kfe.remote.wallet-provisioning.enabled", havingValue = "true", matchIfMissing = true)
public class KfeRemoteFinancialWalletProvisioningClient extends KfeRemoteClientSupport implements FinancialWalletProvisioningPort {

    /** Logger for authenticated remote provisioning outcomes. */
    private static final Logger log = LoggerFactory.getLogger(KfeRemoteFinancialWalletProvisioningClient.class);
    /** Marker keeping these security-relevant events in the authentication audit log category. */
    private static final Marker AUTH_MARKER = MarkerFactory.getMarker("AUTH");
    /** Stable client-safe exception message that omits remote response bodies and credentials. */
    private static final String UNAVAILABLE_MESSAGE = "Primary wallet provisioning is temporarily unavailable.";

    /**
     * Creates the client with a longer read timeout suitable for wallet creation/provisioning.
     *
     * @param restTemplateBuilder Spring HTTP client builder
     * @param baseUrl KFE service root
     * @param internalSecret credential for the internal endpoint
     * @param connectTimeoutMs connection timeout
     * @param readTimeoutMs provisioning response timeout
     */
    public KfeRemoteFinancialWalletProvisioningClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${kfe.remote.base-url:http://kfe-service:8080}") String baseUrl,
            @Value("${kfe.internal.shared-secret:}") String internalSecret,
            @Value("${kfe.remote.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${kfe.remote.wallet-provisioning.read-timeout-ms:180000}") long readTimeoutMs) {
        super(restTemplateBuilder, baseUrl, internalSecret, connectTimeoutMs, readTimeoutMs);
    }

    /**
     * Requests idempotent primary wallet readiness for a user and optional initial address.
     * Null user IDs are ignored; remote transport/HTTP errors are logged with redacted identity and rethrown
     * as a provider-unavailable error.
     *
     * @param userId user whose primary wallet must exist
     * @param initialAddress optional address used during wallet initialization
     * @throws FinancialProviderUnavailableException if KFE cannot complete the request
     */
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
            log.info(AUTH_MARKER, "Remote KFE primary wallet ensured for userRef={}",
                    LogSanitizer.fingerprint(userId.toString()));
        } catch (ResourceAccessException exception) {
            log.warn(AUTH_MARKER, "Remote KFE wallet provisioning transport failure for userRef={} exceptionType={}",
                    LogSanitizer.fingerprint(userId.toString()),
                    exception.getClass().getSimpleName());
            throw new FinancialProviderUnavailableException(UNAVAILABLE_MESSAGE, exception);
        } catch (RestClientResponseException exception) {
            log.warn(
                    AUTH_MARKER,
                    "Remote KFE wallet provisioning HTTP failure for userRef={} status={} exceptionType={}",
                    LogSanitizer.fingerprint(userId.toString()),
                    exception.getStatusCode().value(),
                    exception.getClass().getSimpleName());
            throw new FinancialProviderUnavailableException(UNAVAILABLE_MESSAGE, exception);
        }
    }
}
