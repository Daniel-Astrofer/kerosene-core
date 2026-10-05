package com.kerosene.auth.application.infra.kfe;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.kerosene.common.exception.FinancialProviderUnavailableException;
import com.kerosene.common.infra.logging.LogSanitizer;
import com.kerosene.common.security.workload.InternalServiceRestTemplateFactory;
import com.kerosene.common.security.workload.WorkloadIdentityProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KfeRemoteFinancialWalletProvisioningClientTest {

    @Test
    void postsPrimaryWalletProvisioningRequestToKfe() throws Exception {
        KfeRemoteFinancialWalletProvisioningClient client = new KfeRemoteFinancialWalletProvisioningClient(
                legacyClientFactory("credential"),
                "http://kfe.test",
                100,
                100);
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate(client));
        server.expect(requestTo("http://kfe.test/internal/kfe/wallet-provisioning/primary"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-KFE-Internal-Secret", "credential"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"userId\":42,\"initialAddress\":\"bc1qabc\"}"))
                .andRespond(withSuccess());

        client.ensurePrimaryWalletReady(42L, "bc1qabc");

        server.verify();
    }

    @Test
    void postsPrimaryWalletRepairRequestWithoutInitialAddress() throws Exception {
        KfeRemoteFinancialWalletProvisioningClient client = new KfeRemoteFinancialWalletProvisioningClient(
                legacyClientFactory("credential"),
                "http://kfe.test",
                100,
                100);
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate(client));
        server.expect(requestTo("http://kfe.test/internal/kfe/wallet-provisioning/primary"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-KFE-Internal-Secret", "credential"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"userId\":42,\"initialAddress\":null}"))
                .andRespond(withSuccess());

        client.ensurePrimaryWalletReady(42L, null);

        server.verify();
    }

    @Test
    void rejectsMissingInternalCredentialBeforeCallingKfe() {
        KfeRemoteFinancialWalletProvisioningClient client = new KfeRemoteFinancialWalletProvisioningClient(
                legacyClientFactory(""),
                "http://kfe.test",
                100,
                100);

        assertThrows(IllegalStateException.class, () -> client.ensurePrimaryWalletReady(42L, null));
    }

    @Test
    void transportFailureHasFixedPublicMessageAndSafeAuthLogWithoutRetry() throws Exception {
        KfeRemoteFinancialWalletProvisioningClient client = client();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate(client));
        SocketTimeoutException cause = new SocketTimeoutException("private-host.internal token=sensitive-response");
        AtomicInteger requests = new AtomicInteger();
        server.expect(requestTo("http://kfe.test/internal/kfe/wallet-provisioning/primary"))
                .andExpect(header("X-KFE-Internal-Secret", "credential"))
                .andRespond(request -> {
                    requests.incrementAndGet();
                    throw cause;
                });

        Logger logger = (Logger) LoggerFactory.getLogger(KfeRemoteFinancialWalletProvisioningClient.class);
        ListAppender<ILoggingEvent> appender = attachAppender(logger);
        try {
            FinancialProviderUnavailableException exception = assertThrows(
                    FinancialProviderUnavailableException.class,
                    () -> client.ensurePrimaryWalletReady(42L, null));

            assertEquals("Primary wallet provisioning is temporarily unavailable.", exception.getMessage());
            ResourceAccessException transport = assertInstanceOf(ResourceAccessException.class, exception.getCause());
            assertSame(cause, transport.getCause());
            assertEquals(1, requests.get());
            server.verify();
            assertSafeAuthLog(appender, "exceptionType=ResourceAccessException");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 500, 503})
    void httpFailuresHaveFixedPublicMessageAndSafeAuthLogWithoutRetry(int status) throws Exception {
        KfeRemoteFinancialWalletProvisioningClient client = client();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate(client));
        AtomicInteger requests = new AtomicInteger();
        server.expect(requestTo("http://kfe.test/internal/kfe/wallet-provisioning/primary"))
                .andExpect(header("X-KFE-Internal-Secret", "credential"))
                .andRespond(request -> {
                    requests.incrementAndGet();
                    return withStatus(HttpStatus.valueOf(status))
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"message\":\"private-host.internal token=sensitive-response\"}")
                            .createResponse(request);
                });

        Logger logger = (Logger) LoggerFactory.getLogger(KfeRemoteFinancialWalletProvisioningClient.class);
        ListAppender<ILoggingEvent> appender = attachAppender(logger);
        try {
            FinancialProviderUnavailableException exception = assertThrows(
                    FinancialProviderUnavailableException.class,
                    () -> client.ensurePrimaryWalletReady(42L, null));

            assertEquals("Primary wallet provisioning is temporarily unavailable.", exception.getMessage());
            RestClientResponseException response = assertInstanceOf(RestClientResponseException.class, exception.getCause());
            assertEquals(status, response.getStatusCode().value());
            assertEquals(1, requests.get());
            server.verify();
            assertSafeAuthLog(appender, "status=" + status);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private KfeRemoteFinancialWalletProvisioningClient client() {
        return new KfeRemoteFinancialWalletProvisioningClient(
                legacyClientFactory("credential"), "http://kfe.test", 100, 100);
    }

    private ListAppender<ILoggingEvent> attachAppender(Logger logger) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void assertSafeAuthLog(ListAppender<ILoggingEvent> appender, String expectedMetadata) {
        assertEquals(1, appender.list.size());
        ILoggingEvent event = appender.list.getFirst();
        assertTrue(event.getMarkerList().stream().anyMatch(marker -> marker.getName().equals("AUTH")));
        assertNull(event.getThrowableProxy());
        String message = event.getFormattedMessage();
        assertTrue(message.contains(expectedMetadata));
        assertTrue(message.contains("userRef=" + LogSanitizer.fingerprint("42")));
        assertFalse(message.contains("userId=42"));
        assertFalse(message.contains("http://"));
        assertFalse(message.contains("private-host.internal"));
        assertFalse(message.contains("sensitive-response"));
        assertFalse(message.contains("credential"));
    }

    private RestTemplate restTemplate(KfeRemoteFinancialWalletProvisioningClient client) throws Exception {
        Field field = KfeRemoteClientSupport.class.getDeclaredField("restTemplate");
        field.setAccessible(true);
        return (RestTemplate) field.get(client);
    }

    private InternalServiceRestTemplateFactory legacyClientFactory(String secret) {
        return new InternalServiceRestTemplateFactory(
                new WorkloadIdentityProperties().toConfig(), null, secret);
    }
}
