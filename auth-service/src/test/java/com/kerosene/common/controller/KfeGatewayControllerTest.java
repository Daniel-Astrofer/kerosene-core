package com.kerosene.common.controller;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KfeGatewayControllerTest {

    private RestTemplate restTemplate;
    private KfeGatewayController controller;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        controller = new KfeGatewayController(
                "http://kfe-service:8080",
                "test-secret",
                3000,
                30000);
        // Inject mock via reflection since constructor creates its own RestTemplate
        injectRestTemplate(restTemplate);
    }

    private void injectRestTemplate(RestTemplate rt) {
        try {
            var field = KfeGatewayController.class.getDeclaredField("restTemplate");
            field.setAccessible(true);
            field.set(controller, rt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void timeoutReturns504GatewayTimeout() {
        SocketTimeoutException timeout = new SocketTimeoutException("Read timed out");
        ResourceAccessException rae = new ResourceAccessException("I/O error", timeout);
        when(restTemplate.exchange(any(String.class), any(HttpMethod.class), any(), eq(String.class)))
                .thenThrow(rae);

        var request = new MockHttpServletRequest("GET", "/kfe/wallets/test");
        var response = controller.proxy(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody()).contains("SYS_504");
    }

    @Test
    void otherExceptionsReturn502BadGateway() {
        when(restTemplate.exchange(any(String.class), any(HttpMethod.class), any(), eq(String.class)))
                .thenThrow(new RuntimeException("Something broke"));

        var request = new MockHttpServletRequest("POST", "/kfe/transactions");
        var response = controller.proxy(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).contains("SYS_502");
    }

    @Test
    void normalProxyReturnsUpstreamResponse() {
        var upstreamResponse = new org.springframework.http.ResponseEntity<>(
                "{\"success\":true}", org.springframework.http.HttpStatus.OK);
        when(restTemplate.exchange(any(String.class), any(HttpMethod.class), any(), eq(String.class)))
                .thenReturn(upstreamResponse);

        var request = new MockHttpServletRequest("GET", "/kfe/wallets/test");
        var response = controller.proxy(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("success");
    }
}
