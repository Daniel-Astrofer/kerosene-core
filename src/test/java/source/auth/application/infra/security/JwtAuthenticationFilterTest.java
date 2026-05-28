package source.auth.application.infra.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.HandlerExceptionResolver;
import source.auth.application.service.validation.jwt.JwtService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", "01234567890123456789012345678901");
        filter = new JwtAuthenticationFilter(jwtService, mock(HandlerExceptionResolver.class));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPopulateUserAuthorityForRegularToken() throws Exception {
        String token = jwtService.generateToken(1L, List.of("USER"));
        MockHttpServletRequest request = request(token);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> "ROLE_USER".equals(authority.getAuthority())));
        assertFalse(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())));
    }

    @Test
    void shouldPopulateAdminAuthorityOnlyWhenTokenCarriesAdminRole() throws Exception {
        String token = jwtService.generateToken(2L, List.of("ADMIN"));
        MockHttpServletRequest request = request(token);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())));
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/audit/trigger");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
