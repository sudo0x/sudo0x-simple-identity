package com.sudo0x.simple.identity.common.config;

import com.sudo0x.simple.identity.common.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudo0x.simple.identity.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AppProperties props;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // CSRF is disabled. Rationale:
            // - For Bearer-token clients (API, mobile): cookies are not used; CSRF is not applicable.
            // - For cookie clients (cookie.enabled=true): CSRF is mitigated by the HttpOnly +
            //   SameSite=Lax/Strict attributes set on both the access and refresh token cookies.
            //   SameSite=Lax prevents cross-site POST requests from including the cookie.
            //   Note: if SameSite=None is ever required, stateless CSRF tokens must be added.
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .contentTypeOptions(cto -> {})
                .cacheControl(cc -> {})
            )
            .authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/forgot-password").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/reset-password").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/verify-email").permitAll()
                // JWKS public key discovery
                .requestMatchers(HttpMethod.GET, "/.well-known/jwks.json").permitAll()
                // Actuator health (public), others restricted
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                // OpenAPI (disabled in prod via config)
                .requestMatchers("/swagger-ui/**", "/api-docs/**", "/swagger-ui.html").permitAll()
                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, authException) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(res.getWriter(),
                        ApiResponse.error("Authentication required")
                            .withRequestId((String) req.getAttribute("requestId")));
                })
                .accessDeniedHandler((req, res, deniedException) -> {
                    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(res.getWriter(),
                        ApiResponse.error("Access denied")
                            .withRequestId((String) req.getAttribute("requestId")));
                })
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Argon2id with OWASP-recommended parameters
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        AppProperties.SecurityProperties.CorsProperties cors = props.security().cors();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(cors.allowedOrigins());
        config.setAllowedMethods(cors.allowedMethods());
        config.setAllowedHeaders(cors.allowedHeaders());
        config.setMaxAge(cors.maxAge());
        // Allow credentials (cookies) when cookie-based auth is enabled.
        // Requires that allowedOrigins does not contain "*" — always use specific origins in production.
        config.setAllowCredentials(props.cookie().enabled());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
