package source.auth.application.infra.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityCorsConfigurationTest {

    @Test
    void corsAllowsWebAdminOperationalHeaders() {
        CorsConfigurationSource source = new Security()
                .corsConfigurationSource("http://adminexamplehiddenservice.onion");
        HttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/v1/audit/config");
        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertNotNull(configuration);
        assertTrue(configuration.getAllowedHeaders().contains("Authorization"));
        assertTrue(configuration.getAllowedHeaders().contains("X-Admin-Token"));
        assertTrue(configuration.getAllowedHeaders().contains("Idempotency-Key"));
        assertTrue(configuration.getAllowedHeaders().contains("X-Owner-TOTP"));
        assertTrue(configuration.getAllowedHeaders().contains("X-Hardware-Signature"));
    }

    @Test
    void cspAllowsLocalFlutterWebRuntimeWithoutCdn() {
        String csp = Security.webAdminContentSecurityPolicy();

        assertTrue(csp.contains("default-src 'self'"));
        assertTrue(csp.contains("script-src 'self'"));
        assertTrue(csp.contains("'wasm-unsafe-eval'"));
        assertTrue(csp.contains("worker-src 'self' blob:"));
        assertTrue(csp.contains("style-src 'self' 'unsafe-inline'"));
        assertTrue(csp.contains("connect-src 'self' ws: wss:"));
    }
}
