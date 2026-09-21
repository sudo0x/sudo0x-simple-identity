package com.sudo0x.simple.identity.common.security;

import com.sudo0x.simple.identity.authentication.service.JwtService;
import com.sudo0x.simple.identity.common.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AppProperties props;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null) {
            jwtService.validateAndParse(token).ifPresent(principal -> {
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                principal.roles().forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
                principal.permissions().forEach(p -> authorities.add(new SimpleGrantedAuthority("permission:" + p)));

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        // 1. Prefer Authorization header (for API clients, mobile apps, server-to-server)
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }

        // 2. Fall back to HttpOnly cookie (for browser-based SPA/SSR clients)
        if (props.cookie().enabled() && request.getCookies() != null) {
            String cookieName = props.cookie().accessTokenName();
            return Arrays.stream(request.getCookies())
                    .filter(c -> cookieName.equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }
}
