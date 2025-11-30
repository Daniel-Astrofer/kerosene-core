package source.auth.application.infra.security;

import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerExceptionResolver;
import source.auth.AuthExceptions;
import source.auth.application.service.device.UserDeviceService;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
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
    private final HandlerExceptionResolver resolver;


    public JwtAuthenticationFilter(
            @Qualifier("JwtService") JwtServicer jwtService,
            @Qualifier("handlerExceptionResolver")HandlerExceptionResolver resolver) {
        this.jwtService = jwtService;
        this.resolver = resolver;
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {

            String token = header.substring(7);
            UsernamePasswordAuthenticationToken auth = null;
            try{
                auth = new UsernamePasswordAuthenticationToken(jwtService.extractId(token), token, Collections.singletonList(() -> "USER"));
                if (!jwtService.extractDevice(token).equals(request.getHeader("X-Device-Hash"))){
                    throw new Exception("");
                }
            }catch (Exception e){
                resolver.resolveException(request,response,null, new AuthExceptions.UnrrecognizedDevice("invalid session"));
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
