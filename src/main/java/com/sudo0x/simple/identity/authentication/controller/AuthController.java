package com.sudo0x.simple.identity.authentication.controller;

import com.sudo0x.simple.identity.authentication.dto.*;
import com.sudo0x.simple.identity.authentication.service.AuthService;
import com.sudo0x.simple.identity.common.config.AppProperties;
import com.sudo0x.simple.identity.common.exception.InvalidRefreshTokenException;
import com.sudo0x.simple.identity.common.response.ApiResponse;
import com.sudo0x.simple.identity.common.security.CurrentUser;
import com.sudo0x.simple.identity.common.security.SecurityPrincipal;
import com.sudo0x.simple.identity.common.web.CookieUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login, logout, and token management")
public class AuthController {

    private final AuthService authService;
    private final CookieUtil cookieUtil;
    private final AppProperties props;

    @PostMapping("/login")
    @Operation(summary = "Authenticate with username and password")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        LoginResponse response = authService.login(request,
                getIp(httpRequest), getUserAgent(httpRequest));

        if (props.cookie().enabled()) {
            cookieUtil.setAccessTokenCookie(httpResponse, response.accessToken());
            cookieUtil.setRefreshTokenCookie(httpResponse, response.refreshToken());
        }

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using a valid refresh token")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            // required=false allows browser clients to call this endpoint with no body
            // when cookie.enabled=true — the refresh token is read from the HttpOnly cookie.
            @RequestBody(required = false) RefreshRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String refreshToken = resolveRefreshToken(
                request != null ? request.refreshToken() : null, httpRequest);

        LoginResponse response = authService.refresh(refreshToken,
                getIp(httpRequest), getUserAgent(httpRequest));

        if (props.cookie().enabled()) {
            cookieUtil.setAccessTokenCookie(httpResponse, response.accessToken());
            cookieUtil.setRefreshTokenCookie(httpResponse, response.refreshToken());
        }

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout (revoke current refresh token)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> logout(
            // required=false allows browser clients to call this endpoint with no body
            // when cookie.enabled=true — the refresh token is read from the HttpOnly cookie.
            @RequestBody(required = false) LogoutRequest request,
            @CurrentUser SecurityPrincipal principal,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String refreshToken = resolveRefreshToken(
                request != null ? request.refreshToken() : null, httpRequest);

        authService.logout(refreshToken, principal.userId(),
                getIp(httpRequest), getUserAgent(httpRequest));

        if (props.cookie().enabled()) {
            cookieUtil.clearAuthCookies(httpResponse);
        }

        return ResponseEntity.ok(ApiResponse.ok(null, "Logged out successfully"));
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Logout from all sessions", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> logoutAll(
            @CurrentUser SecurityPrincipal principal,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        authService.logoutAll(principal.userId(),
                getIp(httpRequest), getUserAgent(httpRequest));

        if (props.cookie().enabled()) {
            cookieUtil.clearAuthCookies(httpResponse);
        }

        return ResponseEntity.ok(ApiResponse.ok(null, "Logged out from all sessions"));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user info", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<MeResponse>> me(@CurrentUser SecurityPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(authService.getMe(principal.userId())));
    }

    // -------------------------------------------------------------------------

    /**
     * Resolves the refresh token from either:
     * 1. The request body (for API clients — mobile, server-to-server, Swagger)
     * 2. The refresh_token HttpOnly cookie (for browser clients when cookie.enabled=true)
     *
     * Throws InvalidRefreshTokenException if no token is found in either location.
     */
    private String resolveRefreshToken(String bodyToken, HttpServletRequest request) {
        // Prefer body — supports all clients, including non-browser
        if (StringUtils.hasText(bodyToken)) {
            return bodyToken;
        }
        // Fall back to cookie — supports browser clients with HttpOnly cookie auth
        if (props.cookie().enabled() && request.getCookies() != null) {
            String cookieName = props.cookie().refreshTokenName();
            return Arrays.stream(request.getCookies())
                    .filter(c -> cookieName.equals(c.getName()))
                    .map(Cookie::getValue)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElseThrow(InvalidRefreshTokenException::new);
        }
        throw new InvalidRefreshTokenException();
    }

    private String getIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String getUserAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return ua != null ? ua.substring(0, Math.min(ua.length(), 256)) : null;
    }
}
