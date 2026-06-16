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
    void shouldNotLetSpoofedAuthorizationHeaderBypassPublicAuthIdentityBucket() throws Exception {
        RedisServicer redisServicer = mock(RedisServicer.class);
        when(redisServicer.increment(anyString())).thenReturn(1L);

        RateLimitFilter filter = new RateLimitFilter(redisServicer, new ObjectMapper());
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest firstRequest = jsonRequest("/auth/login", "{\"username\":\"alice\"}");
        firstRequest.addHeader("Authorization", "Bearer attacker-rotated-1");
        MockHttpServletRequest secondRequest = jsonRequest("/auth/login", "{\"username\":\"alice\"}");
        secondRequest.addHeader("Authorization", "Bearer attacker-rotated-2");

        filter.doFilter(firstRequest, new MockHttpServletResponse(), chain);
        filter.doFilter(secondRequest, new MockHttpServletResponse(), chain);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(redisServicer, times(2)).increment(captor.capture());

        assertEquals(captor.getAllValues().get(0), captor.getAllValues().get(1));
        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    void shouldBucketAuthenticatedFundRequestsByAuthorizationBeforeIdempotencyKey() throws Exception {
        RedisServicer redisServicer = mock(RedisServicer.class);
        when(redisServicer.increment(anyString())).thenReturn(1L);

        RateLimitFilter filter = new RateLimitFilter(redisServicer, new ObjectMapper());
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest firstRequest = jsonRequest(
                "/transactions/network/onchain/send",
                "{\"idempotencyKey\":\"idem-1\",\"fromWalletName\":\"main\"}");
        firstRequest.addHeader("Authorization", "Bearer stable-jwt");
        MockHttpServletRequest secondRequest = jsonRequest(
                "/transactions/network/onchain/send",
                "{\"idempotencyKey\":\"idem-2\",\"fromWalletName\":\"main\"}");
        secondRequest.addHeader("Authorization", "Bearer stable-jwt");

        filter.doFilter(firstRequest, new MockHttpServletResponse(), chain);
        filter.doFilter(secondRequest, new MockHttpServletResponse(), chain);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(redisServicer, times(2)).increment(captor.capture());

        assertEquals(captor.getAllValues().get(0), captor.getAllValues().get(1));
        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    void shouldBucketPublicAuthRequestsWithoutIdentityByNetwork() throws Exception {
        RedisServicer redisServicer = mock(RedisServicer.class);
        when(redisServicer.increment(anyString())).thenReturn(1L);

        RateLimitFilter filter = new RateLimitFilter(redisServicer, new ObjectMapper());
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest firstRequest = jsonRequest("/auth/login", "{\"payload\":\"one\"}");
        firstRequest.addHeader("Authorization", "Bearer attacker-rotated-1");
        MockHttpServletRequest secondRequest = jsonRequest("/auth/login", "{\"payload\":\"two\"}");
        secondRequest.addHeader("Authorization", "Bearer attacker-rotated-2");

        filter.doFilter(firstRequest, new MockHttpServletResponse(), chain);
        filter.doFilter(secondRequest, new MockHttpServletResponse(), chain);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(redisServicer, times(2)).increment(captor.capture());

        assertEquals(captor.getAllValues().get(0), captor.getAllValues().get(1));
        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    void shouldRejectWhenPublicRouteExceedsLimit() throws Exception {
        RedisServicer redisServicer = mock(RedisServicer.class);
        when(redisServicer.increment(anyString())).thenReturn(11L);

        RateLimitFilter filter = new RateLimitFilter(redisServicer, new ObjectMapper());
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(jsonRequest("/auth/login", "{\"username\":\"alice\"}"), response, chain);

        assertEquals(429, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void shouldApplyGranularLimitToFundMovementRoutes() throws Exception {
        RedisServicer redisServicer = mock(RedisServicer.class);
        when(redisServicer.increment(anyString())).thenReturn(7L);

        RateLimitFilter filter = new RateLimitFilter(redisServicer, new ObjectMapper());
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest request = jsonRequest(
                "/transactions/network/onchain/send",
                "{\"fromWalletName\":\"main\",\"idempotencyKey\":\"idem-1\"}");
        request.addHeader("Authorization", "Bearer jwt");

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);

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
