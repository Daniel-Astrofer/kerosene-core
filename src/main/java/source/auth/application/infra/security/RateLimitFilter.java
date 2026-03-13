package source.auth.application.infra.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import source.auth.application.service.cache.contracts.RedisServicer;

import java.io.IOException;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisServicer redisService;
    private static final int MAX_REQUESTS_PER_MINUTE = 100; // General limit
    private static final int PUBLIC_MAX_REQUESTS_PER_MINUTE = 20; // Stricter for public endpoints

    public RateLimitFilter(RedisServicer redisService) {
        this.redisService = redisService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String ip = request.getRemoteAddr();
        String uri = request.getRequestURI();

        // Use a simpler key based on IP and current minute
        long currentMinute = System.currentTimeMillis() / 60000;
        String key = "ratelimit:" + ip + ":" + currentMinute;

        // Determine limit based on endpoint
        int limit = uri.startsWith("/auth/") ? PUBLIC_MAX_REQUESTS_PER_MINUTE : MAX_REQUESTS_PER_MINUTE;

        Long requests = redisService.increment(key);

        if (requests == 1) {
            redisService.expire(key, 60); // Expire after 1 minute
        }

        if (requests > limit) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Too many requests");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
