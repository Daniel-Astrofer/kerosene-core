package source.auth.application.infra.security;

import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;
import source.auth.AuthExceptions;
import source.auth.application.service.device.UserDeviceService;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import source.auth.application.service.validation.jwt.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import source.auth.model.entity.UserDataBase;
import source.auth.model.entity.UserDevice;
import source.wallet.exceptions.WalletExceptions;

import java.io.IOException;
import java.security.SignatureException;
import java.util.Collections;
import java.util.Optional;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    // Paths where the HTTP filter should NOT abort on JWT failure.
    // WebSocket upgrade requests are authenticated at the STOMP layer instead.
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final java.util.List<String> SKIP_BLOCK_PATHS = java.util.List.of("/ws/**");

    private final JwtServicer jwtService;
    private final JwtService jwtServiceImpl;
    private final HandlerExceptionResolver resolver;

    public JwtAuthenticationFilter(
            @Qualifier("JwtService") JwtServicer jwtService,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.jwtService = jwtService;
        this.jwtServiceImpl = (JwtService) jwtService;
        this.resolver = resolver;
    }

    private boolean isSkipBlockPath(String path) {
        return SKIP_BLOCK_PATHS.stream().anyMatch(p -> PATH_MATCHER.match(p, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        String token = null;

        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
        } else if (request.getParameter("token") != null) {
            token = request.getParameter("token");
        }

        if (token != null) {
            UsernamePasswordAuthenticationToken auth = null;
            try {
                Long userId = jwtService.extractId(token);
                String deviceHash = ""; // Device hash is no longer enforced

                auth = new UsernamePasswordAuthenticationToken(userId, token, Collections.singletonList(() -> "USER"));

                // Verificar se o token precisa ser renovado
                if (jwtServiceImpl.shouldRenewToken(token)) {
                    String newToken = jwtService.generateToken(userId, deviceHash);
                    // Adicionar novo token no header da resposta
                    response.setHeader("X-New-Token", newToken);
                }

            } catch (Exception e) {
                log.warn("JWT Authentication Error for path {}: {}", request.getServletPath(), e.getMessage());
                // For WebSocket upgrade paths (/ws/**), do NOT abort the request.
                // The STOMP-level interceptor in WebSocketConfig handles authentication
                // AFTER the HTTP upgrade. Returning 401 here prevents the upgrade entirely.
                if (isSkipBlockPath(request.getServletPath())) {
                    log.debug("Skipping JWT block for WS path: {}", request.getServletPath());
                } else {
                    resolver.resolveException(request, response, null,
                            new AuthExceptions.UnrrecognizedDevice("invalid session: " + e.getMessage()));
                    return;
                }
            }
            if (auth != null) {
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }

}
