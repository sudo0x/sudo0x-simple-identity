package com.sudo0x.simple.identity.common.web;

import com.sudo0x.simple.identity.common.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class CookieUtil {

    private final AppProperties props;

    public void setAccessTokenCookie(HttpServletResponse response, String token) {
        setCookie(response, props.cookie().accessTokenName(), token,
                props.jwt().accessTokenExpiration());
    }

    public void setRefreshTokenCookie(HttpServletResponse response, String token) {
        setCookie(response, props.cookie().refreshTokenName(), token,
                props.jwt().refreshTokenExpiration());
    }

    public void clearAuthCookies(HttpServletResponse response) {
        clearCookie(response, props.cookie().accessTokenName());
        clearCookie(response, props.cookie().refreshTokenName());
    }

    // -------------------------------------------------------------------------

    private void setCookie(HttpServletResponse response, String name, String value, Duration maxAge) {
        AppProperties.CookieProperties c = props.cookie();
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(c.httpOnly())
                .secure(c.secure())
                .sameSite(c.sameSite())
                .path(c.path())
                .maxAge(maxAge)
                .domain(c.domain().isBlank() ? null : c.domain())
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearCookie(HttpServletResponse response, String name) {
        AppProperties.CookieProperties c = props.cookie();
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(c.httpOnly())
                .secure(c.secure())
                .sameSite(c.sameSite())
                .path(c.path())
                .maxAge(Duration.ZERO)
                .domain(c.domain().isBlank() ? null : c.domain())
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
