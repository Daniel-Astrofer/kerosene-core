package source.auth.application.infra.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.HandlerExceptionResolver;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class Security {

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http,
                        JwtAuthenticationFilter jwtAuthenticationFilter,
                        RateLimitFilter rateLimitFilter,
                        ParanoidSecurityFilter paranoidFilter,
                        org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource)
                        throws Exception {
                http

                                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                                .csrf(AbstractHttpConfigurer::disable)
                                .headers(headers -> headers
                                                .contentSecurityPolicy(
                                                                csp -> csp.policyDirectives("default-src 'self'"))
                                                .frameOptions(frame -> frame.deny())
                                                .xssProtection(xss -> xss.headerValue(
                                                                org.springframework.security.web.header.writers.XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                                                .httpStrictTransportSecurity(hsts -> hsts
                                                                .includeSubDomains(true)
                                                                .maxAgeInSeconds(31536000))) // 1 year

                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                                .authorizeHttpRequests(auth -> auth

                                                .requestMatchers("/auth/signup").permitAll()
                                                .requestMatchers("/auth/signup/totp/verify").permitAll()
                                                .requestMatchers("/auth/login").permitAll()
                                                .requestMatchers("/auth/login/totp/verify").permitAll()
                                                .requestMatchers("/auth/passkey/challenge").permitAll()
                                                .requestMatchers("/auth/passkey/verify").permitAll()
                                                .requestMatchers("/auth/passkey/onboarding/start").permitAll()
                                                .requestMatchers("/auth/passkey/onboarding/finish").permitAll()
                                                .requestMatchers("/auth/recovery/emergency/start").permitAll()
                                                .requestMatchers("/auth/recovery/emergency/finish").permitAll()
                                                .requestMatchers("/auth/pow/challenge").permitAll()
                                                .requestMatchers("/voucher/**").permitAll()
                                                .requestMatchers(
                                                                "/v3/api-docs",
                                                                "/v3/api-docs/**",
                                                                "/swagger-ui",
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html",
                                                                "/swagger-resources",
                                                                "/swagger-resources/**",
                                                                "/configuration/ui",
                                                                "/configuration/security",
                                                                "/webjars/**",
                                                                "/error",
                                                                "/ws/**")
                                                .permitAll()
                                                .anyRequest().authenticated())
                                .addFilterBefore(paranoidFilter, UsernamePasswordAuthenticationFilter.class)
                                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        @Bean
        public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource(
                        @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:8080}") String allowedOrigins) {
                org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
                java.util.List<String> origins = java.util.Arrays.stream(allowedOrigins.split(","))
                                .map(String::trim)
                                .filter(origin -> !origin.isEmpty())
                                .toList();
                if (origins.isEmpty() || origins.contains("*")) {
                        throw new IllegalStateException(
                                        "app.cors.allowed-origins must explicitly list trusted Flutter app origins; wildcard CORS is not allowed.");
                }
                configuration.setAllowedOrigins(origins);
                configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(java.util.List.of(
                                "Authorization",
                                "Content-Type",
                                "Digest",
                                "X-Requested-With",
                                "X-Idempotency-Key"));
                configuration.setExposedHeaders(java.util.List.of("X-New-Token"));
                configuration.setAllowCredentials(true);
                org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }

        @Bean
        public JwtAuthenticationFilter jwtAuthenticationFilter(JwtServicer jwtService,
                        @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
                return new JwtAuthenticationFilter(jwtService, resolver);
        }

        @Bean
        public UserDetailsService userDetailsService() {
                return username -> {
                        throw new UsernameNotFoundException(
                                        "UserDetails service not strictly used, managed by JWT filter");
                };
        }

}
