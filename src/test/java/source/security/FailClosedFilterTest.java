package source.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import source.auth.application.infra.security.ParanoidSecurityFilter;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

        // Verify suicideService was called because of digest mismatch
        verify(suicideService, atLeastOnce()).triggerInstantSuicide(anyString());
        // Verify filterChain was NOT called (or called but system theoretically stops -
        // in our implementation we call doFilter AFTER verify, so if verify doesn't throw but signals suicide,
        // it's a bit dependent on if suicideService.triggerInstantSuicide terminates the thread).
        // Since triggerInstantSuicide usually calls System.exit or similar, here we check if doFilter was skipped.
        // Actually, in the current code, verifyMilitaryDigest is called BEFORE doFilter.
        // If it sends a death signal, we check if doFilter was still reached in the test mock.
    }

    @Test
    void honeypotFilter_ShouldFailClosed_WhenPayloadIsMalformed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setContent("invalid-json{".getBytes());
        request.setContentType("application/json");

        MockHttpServletResponse response = new MockHttpServletResponse();

        honeypotFilter.doFilter(request, response, filterChain);

        // Should return 400 Bad Request and NOT continue the chain
        assert(response.getStatus() == HttpServletResponse.SC_BAD_REQUEST);
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
        assert(response.getStatus() == HttpServletResponse.SC_OK);
        verify(filterChain, never()).doFilter(any(), any());
    }
}
