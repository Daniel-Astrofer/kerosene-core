package source.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import source.auth.application.infra.security.ParanoidSecurityFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class FailClosedFilterTest {

    private SuicideService suicideService;
    private ParanoidSecurityFilter paranoidFilter;
    private HoneypotRequestFilter honeypotFilter;
    private FilterChain filterChain;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        suicideService = mock(SuicideService.class);
        paranoidFilter = new ParanoidSecurityFilter(suicideService);
        objectMapper = new ObjectMapper();
        honeypotFilter = new HoneypotRequestFilter(objectMapper);
        filterChain = mock(FilterChain.class);
    }

    @Test
    void paranoidFilter_ShouldTriggerSuicide_WhenDigestIsWrong() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ledger/tx");
        request.addHeader("Digest", "SHA-256=wronghash");
        request.setContent("{\"amount\":100}".getBytes());
        request.setContentType("application/json");

        MockHttpServletResponse response = new MockHttpServletResponse();

        paranoidFilter.doFilter(request, response, filterChain);

        verify(suicideService, atLeastOnce()).triggerInstantSuicide(anyString());
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void paranoidFilter_ShouldForward_WhenDigestIsValidAndPreserveBody() throws Exception {
        byte[] body = "{\"amount\":100}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ledger/tx");
        request.addHeader("Digest", "SHA-256=" + sha256(body));
        request.setContent(body);
        request.setContentType("application/json");

        MockHttpServletResponse response = new MockHttpServletResponse();

        paranoidFilter.doFilter(request, response, filterChain);

        verify(suicideService, never()).triggerInstantSuicide(anyString());
        verify(filterChain).doFilter(argThat(servletRequest -> {
            try {
                assertArrayEquals(body, servletRequest.getInputStream().readAllBytes());
                return true;
            } catch (Exception e) {
                return false;
            }
        }), any());
    }

    @Test
    void paranoidFilter_ShouldForward_WhenDigestIsAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ledger/tx");
        request.setContent("{\"amount\":100}".getBytes(StandardCharsets.UTF_8));
        request.setContentType("application/json");

        MockHttpServletResponse response = new MockHttpServletResponse();

        paranoidFilter.doFilter(request, response, filterChain);

        verify(suicideService, never()).triggerInstantSuicide(anyString());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void honeypotFilter_ShouldFailClosed_WhenPayloadIsMalformed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setContent("invalid-json{".getBytes());
        request.setContentType("application/json");

        MockHttpServletResponse response = new MockHttpServletResponse();

        honeypotFilter.doFilter(request, response, filterChain);

        // Should return 400 Bad Request and NOT continue the chain
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void honeypotFilter_ShouldBlock_WhenHoneypotTriggered() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setContent("{\"username\":\"bot\", \"__hp\":\"true\"}".getBytes());
        request.setContentType("application/json");

        MockHttpServletResponse response = new MockHttpServletResponse();

        honeypotFilter.doFilter(request, response, filterChain);

        // Should return 200 (silently blackholed) and NOT continue the chain
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void honeypotFilter_ShouldForward_WhenPayloadIsLegitimate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setContent("{\"username\":\"human\"}".getBytes());
        request.setContentType("application/json");

        MockHttpServletResponse response = new MockHttpServletResponse();

        honeypotFilter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void honeypotFilter_ShouldSkipInspection_WhenRouteIsOutsideAuthFlow() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ledger/tx");
        request.setContent("{\"__hp\":\"triggered\"}".getBytes());
        request.setContentType("application/json");

        MockHttpServletResponse response = new MockHttpServletResponse();

        honeypotFilter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void honeypotFilter_ShouldIgnoreGetRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/login");
        request.setContent("{\"__hp\":\"triggered\"}".getBytes());
        request.setContentType("application/json");

        MockHttpServletResponse response = new MockHttpServletResponse();

        honeypotFilter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        verify(filterChain).doFilter(any(), any());
    }

    private String sha256(byte[] body) throws Exception {
        return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(body));
    }
}
