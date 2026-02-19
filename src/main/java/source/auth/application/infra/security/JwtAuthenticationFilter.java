package source.auth.application.infra.security;

import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {

            String token = header.substring(7);
            UsernamePasswordAuthenticationToken auth = null;
            try {
                Long userId = jwtService.extractId(token);
                String deviceHash = jwtService.extractDevice(token);

                // Relax device hash check for WebSocket handshakes or specific paths if needed
                String path = request.getRequestURI();
                boolean isWs = path != null && path.startsWith("/ws/");
                String requestedDeviceHash = request.getHeader("X-Device-Hash");

                if (!isWs && (requestedDeviceHash == null || !deviceHash.equals(requestedDeviceHash))) {
                    throw new Exception("Invalid session: Device hash mismatch");
                }

                auth = new UsernamePasswordAuthenticationToken(userId, token, Collections.singletonList(() -> "USER"));

                // Verificar se o token precisa ser renovado
                if (jwtServiceImpl.shouldRenewToken(token)) {
                    String newToken = jwtService.generateToken(userId, deviceHash);
                    // Adicionar novo token no header da resposta
                    response.setHeader("X-New-Token", newToken);
                }

            } catch (Exception e) {
                resolver.resolveException(request, response, null,
                        new AuthExceptions.UnrrecognizedDevice("invalid session"));
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

}
