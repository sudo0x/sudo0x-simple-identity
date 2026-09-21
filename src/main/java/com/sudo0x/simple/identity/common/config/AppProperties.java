package com.sudo0x.simple.identity.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "identity")
public record AppProperties(
        JwtProperties jwt,
        SecurityProperties security,
        PasswordProperties password,
        EmailProperties email,
        SeedProperties seed,
        CookieProperties cookie
) {

    public record JwtProperties(
            @DefaultValue("identity-service") String issuer,
            @DefaultValue("PT15M") Duration accessTokenExpiration,
            @DefaultValue("P30D") Duration refreshTokenExpiration,
            @DefaultValue("generate") String privateKeyLocation,
            @DefaultValue("generate") String publicKeyLocation
    ) {}

    public record SecurityProperties(
            @DefaultValue("5") int maxLoginAttempts,
            @DefaultValue("PT15M") Duration lockDuration,
            CorsProperties cors
    ) {
        public record CorsProperties(
                @DefaultValue("http://localhost:3000") List<String> allowedOrigins,
                @DefaultValue("GET,POST,PUT,PATCH,DELETE,OPTIONS") List<String> allowedMethods,
                @DefaultValue("*") List<String> allowedHeaders,
                @DefaultValue("3600") long maxAge
        ) {}
    }

    public record PasswordProperties(
            @DefaultValue("8") int minimumLength,
            @DefaultValue("true") boolean requireUppercase,
            @DefaultValue("true") boolean requireLowercase,
            @DefaultValue("true") boolean requireDigit,
            @DefaultValue("false") boolean requireSpecial
    ) {}

    public record EmailProperties(
            @DefaultValue("PT24H") Duration verificationTokenExpiration,
            @DefaultValue("PT1H") Duration resetTokenExpiration,
            @DefaultValue("false") boolean verificationRequired
    ) {}

    public record SeedProperties(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("admin") String adminUsername,
            @DefaultValue("admin@localhost") String adminEmail,
            @DefaultValue("") String adminPassword
    ) {}

    /**
     * Cookie settings for browser-based clients.
     * When cookie.enabled=true, the auth endpoints set HttpOnly cookies in addition
     * to returning tokens in the response body (supports both SPA and SSR clients).
     */
    public record CookieProperties(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("true") boolean secure,
            @DefaultValue("true") boolean httpOnly,
            @DefaultValue("Lax") String sameSite,
            @DefaultValue("") String domain,
            @DefaultValue("/") String path,
            @DefaultValue("access_token") String accessTokenName,
            @DefaultValue("refresh_token") String refreshTokenName
    ) {}
}
