package com.sudo0x.simple.identity.authentication;

import com.sudo0x.simple.identity.audit.service.AuditService;
import com.sudo0x.simple.identity.common.config.AppProperties;
import com.sudo0x.simple.identity.common.exception.InvalidRefreshTokenException;
import com.sudo0x.simple.identity.session.entity.RefreshToken;
import com.sudo0x.simple.identity.session.repository.RefreshTokenRepository;
import com.sudo0x.simple.identity.session.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock AuditService auditService;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                new AppProperties.JwtProperties("test", Duration.ofMinutes(15), Duration.ofDays(30),
                        "generate", "generate"),
                new AppProperties.SecurityProperties(5, Duration.ofMinutes(15),
                        new AppProperties.SecurityProperties.CorsProperties(List.of(), List.of(), List.of(), 3600)),
                new AppProperties.PasswordProperties(8, true, true, true, false),
                new AppProperties.EmailProperties(Duration.ofHours(24), Duration.ofHours(1), false),
                new AppProperties.SeedProperties(false, "admin", "admin@localhost", ""),
                new AppProperties.CookieProperties(false, true, true, "Lax", "", "/", "access_token", "refresh_token")
        );
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, auditService, props);
    }

    @Test
    void sha256ProducesDeterministicHash() {
        String hash1 = RefreshTokenService.sha256("testvalue");
        String hash2 = RefreshTokenService.sha256("testvalue");
        assertThat(hash1).isEqualTo(hash2).hasSize(64);
    }

    @Test
    void rotateRevokedTokenDetectsReuse() {
        UUID userId = UUID.randomUUID();
        String plaintext = "sometoken";
        String hash = RefreshTokenService.sha256(plaintext);

        RefreshToken revoked = new RefreshToken(userId, hash, Instant.now().plusSeconds(3600));
        revoked.setRevokedAt(Instant.now().minusSeconds(60));  // already revoked

        when(refreshTokenRepository.findByTokenHashForUpdate(hash)).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> refreshTokenService.rotate(plaintext))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("reuse");

        verify(refreshTokenRepository).revokeAllByUserId(eq(userId), any());
    }

    @Test
    void rotateExpiredTokenThrows() {
        String plaintext = "expiredtoken";
        String hash = RefreshTokenService.sha256(plaintext);

        RefreshToken expired = new RefreshToken(UUID.randomUUID(), hash,
                Instant.now().minusSeconds(10));  // expired

        when(refreshTokenRepository.findByTokenHashForUpdate(hash)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.rotate(plaintext))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void rotateValidTokenSucceeds() {
        UUID userId = UUID.randomUUID();
        String plaintext = "validtoken";
        String hash = RefreshTokenService.sha256(plaintext);

        RefreshToken valid = new RefreshToken(userId, hash, Instant.now().plusSeconds(86400));

        when(refreshTokenRepository.findByTokenHashForUpdate(hash)).thenReturn(Optional.of(valid));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String newToken = refreshTokenService.rotate(plaintext);

        assertThat(newToken).isNotBlank().isNotEqualTo(plaintext);
        assertThat(valid.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository, times(2)).save(any());  // new token + old token
    }
}
