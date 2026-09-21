package com.sudo0x.simple.identity.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String REQUEST_ID_ATTR = "requestId";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        request.setAttribute(REQUEST_ID_ATTR, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String header = request.getHeader(REQUEST_ID_HEADER);
        if (StringUtils.hasText(header) && header.length() <= 64) {
            // Sanitize: accept only alphanumeric and common safe chars
            return header.replaceAll("[^a-zA-Z0-9\\-_]", "");
        }
        return UUID.randomUUID().toString();
    }
}
