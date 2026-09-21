package com.sudo0x.simple.identity.authentication;

import com.sudo0x.simple.identity.authentication.service.JwtService;
import com.sudo0x.simple.identity.common.config.AppProperties;
import com.sudo0x.simple.identity.common.security.SecurityPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                new AppProperties.JwtProperties(
                        "test-issuer",
                        Duration.ofMinutes(15),
                        Duration.ofDays(30),
                        "generate",
                        "generate"
                ),
                new AppProperties.SecurityProperties(5, Duration.ofMinutes(15),
                        new AppProperties.SecurityProperties.CorsProperties(
                                java.util.List.of("http://localhost:3000"),
                                java.util.List.of("GET", "POST"),
                                java.util.List.of("*"),
                                3600L
                        )),
                new AppProperties.PasswordProperties(8, true, true, true, false),
                new AppProperties.EmailProperties(Duration.ofHours(24), Duration.ofHours(1), false),
                new AppProperties.SeedProperties(false, "admin", "admin@localhost", ""),
                new AppProperties.CookieProperties(false, true, true, "Lax", "", "/", "access_token", "refresh_token")
        );
        jwtService = new JwtService(props);
        jwtService.init();
    }

    @Test
    void generateAndValidateToken() {
        UUID userId = UUID.randomUUID();
        SecurityPrincipal principal = new SecurityPrincipal(
                userId, "testuser", Set.of("USER"), Set.of("user:read"));

        String token = jwtService.generateAccessToken(principal);

        assertThat(token).isNotBlank();
        Optional<SecurityPrincipal> parsed = jwtService.validateAndParse(token);
        assertThat(parsed).isPresent();
        assertThat(parsed.get().userId()).isEqualTo(userId);
        assertThat(parsed.get().username()).isEqualTo("testuser");
        assertThat(parsed.get().roles()).containsExactly("USER");
        assertThat(parsed.get().permissions()).containsExactly("user:read");
    }

    @Test
    void invalidTokenReturnsEmpty() {
        Optional<SecurityPrincipal> result = jwtService.validateAndParse("not-a-jwt");
        assertThat(result).isEmpty();
    }

    @Test
    void tamperedTokenReturnsEmpty() {
        SecurityPrincipal principal = new SecurityPrincipal(
                UUID.randomUUID(), "alice", Set.of("USER"), Set.of());
        String token = jwtService.generateAccessToken(principal);

        // Tamper with the payload
        String[] parts = token.split("\\.");
        String tampered = parts[0] + ".dGFtcGVyZWQ." + parts[2];
        assertThat(jwtService.validateAndParse(tampered)).isEmpty();
    }

    @Test
    void expiredTokenReturnsEmpty() {
        AppProperties propsShortExpiry = new AppProperties(
                new AppProperties.JwtProperties(
                        "test-issuer",
                        Duration.ofSeconds(-1),  // already expired
                        Duration.ofDays(30),
                        "generate",
                        "generate"
                ),
                new AppProperties.SecurityProperties(5, Duration.ofMinutes(15),
                        new AppProperties.SecurityProperties.CorsProperties(
                                java.util.List.of("http://localhost:3000"),
                                java.util.List.of("GET"),
                                java.util.List.of("*"),
                                3600L
                        )),
                new AppProperties.PasswordProperties(8, true, true, true, false),
                new AppProperties.EmailProperties(Duration.ofHours(24), Duration.ofHours(1), false),
                new AppProperties.SeedProperties(false, "admin", "admin@localhost", ""),
                new AppProperties.CookieProperties(false, true, true, "Lax", "", "/", "access_token", "refresh_token")
        );
        JwtService shortJwtService = new JwtService(propsShortExpiry);
        shortJwtService.init();

        SecurityPrincipal principal = new SecurityPrincipal(
                UUID.randomUUID(), "bob", Set.of(), Set.of());
        String token = shortJwtService.generateAccessToken(principal);
        assertThat(jwtService.validateAndParse(token)).isEmpty();
    }

    @Test
    void publicKeyIsExposed() {
        assertThat(jwtService.getPublicKey()).isNotNull();
    }
}
