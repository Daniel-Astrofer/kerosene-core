package source.auth.application.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import source.auth.application.service.cache.contracts.RedisServicer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    @Test
    void shouldBucketPublicAuthRequestsByUsernameInsteadOfRemoteAddressAlone() throws Exception {
        RedisServicer redisServicer = mock(RedisServicer.class);
        when(redisServicer.increment(anyString())).thenReturn(1L);

        RateLimitFilter filter = new RateLimitFilter(redisServicer, new ObjectMapper());
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest aliceRequest = jsonRequest("/auth/login", "{\"username\":\"alice\"}");
        MockHttpServletRequest bobRequest = jsonRequest("/auth/login", "{\"username\":\"bob\"}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(aliceRequest, response, chain);
        filter.doFilter(bobRequest, response, chain);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(redisServicer, times(2)).increment(captor.capture());

        assertEquals(2, captor.getAllValues().size());
        assertNotEquals(captor.getAllValues().get(0), captor.getAllValues().get(1));
        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    void shouldRejectWhenPublicRouteExceedsLimit() throws Exception {
        RedisServicer redisServicer = mock(RedisServicer.class);
        when(redisServicer.increment(anyString())).thenReturn(21L);

        RateLimitFilter filter = new RateLimitFilter(redisServicer, new ObjectMapper());
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(jsonRequest("/auth/login", "{\"username\":\"alice\"}"), response, chain);

        assertEquals(429, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    private MockHttpServletRequest jsonRequest(String uri, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr("172.20.0.5");
        request.setContentType("application/json");
        request.setContent(body.getBytes());
        return request;
    }
}
