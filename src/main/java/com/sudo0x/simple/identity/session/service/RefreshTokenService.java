package com.sudo0x.simple.identity.session.service;

import com.sudo0x.simple.identity.audit.entity.AuditEventType;
import com.sudo0x.simple.identity.audit.service.AuditService;
import com.sudo0x.simple.identity.common.config.AppProperties;
import com.sudo0x.simple.identity.common.exception.InvalidRefreshTokenException;
import com.sudo0x.simple.identity.session.entity.RefreshToken;
import com.sudo0x.simple.identity.session.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 48;
    private final SecureRandom secureRandom = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;
    private final AppProperties props;

    /**
     * Issue a new refresh token for the given user and return the plaintext token.
     * The plaintext is never stored — only its SHA-256 hash is persisted.
     */
    @Transactional
    public String issue(UUID userId) {
        String plaintext = generatePlaintext();
        String hash = sha256(plaintext);
        Instant expiresAt = Instant.now().plus(props.jwt().refreshTokenExpiration());
        refreshTokenRepository.save(new RefreshToken(userId, hash, expiresAt));
        return plaintext;
    }

    /**
     * Rotate a refresh token: validate, revoke the old one, issue a new one.
     * Implements reuse detection: if a revoked token is presented, all sessions for that user are revoked.
     *
     * @return new plaintext refresh token
     */
    @Transactional
    public String rotate(String plaintextToken) {
        String hash = sha256(plaintextToken);

        // Use pessimistic lock to prevent race conditions on the same token
        RefreshToken existing = refreshTokenRepository.findByTokenHashForUpdate(hash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (existing.isRevoked()) {
            // Reuse of a rotated token detected — revoke all sessions for this user
            log.warn("Refresh token reuse detected for userId={}", existing.getUserId());
            refreshTokenRepository.revokeAllByUserId(existing.getUserId(), Instant.now());
            auditService.record(existing.getUserId(), AuditEventType.TOKEN_REUSE_DETECTED,
                    "All sessions revoked due to token reuse", null, null);
            throw new InvalidRefreshTokenException("Token reuse detected. All sessions have been invalidated.");
        }

        if (existing.isExpired()) {
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        // Revoke the old token
        existing.setRevokedAt(Instant.now());

        // Issue new token
        String newPlaintext = generatePlaintext();
        String newHash = sha256(newPlaintext);
        Instant expiresAt = Instant.now().plus(props.jwt().refreshTokenExpiration());
        RefreshToken newToken = new RefreshToken(existing.getUserId(), newHash, expiresAt);
        RefreshToken saved = refreshTokenRepository.save(newToken);

        existing.setReplacedBy(saved.getId());
        refreshTokenRepository.save(existing);

        return newPlaintext;
    }

    /**
     * Get the userId associated with a valid refresh token (for building the new JWT).
     */
    @Transactional(readOnly = true)
    public UUID getUserIdFromToken(String plaintextToken) {
        String hash = sha256(plaintextToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(InvalidRefreshTokenException::new);
        if (!token.isValid()) {
            throw new InvalidRefreshTokenException();
        }
        return token.getUserId();
    }

    @Transactional
    public void revoke(String plaintextToken) {
        String hash = sha256(plaintextToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(t -> {
            if (!t.isRevoked()) {
                t.setRevokedAt(Instant.now());
                refreshTokenRepository.save(t);
            }
        });
    }

    @Transactional
    public int revokeAllForUser(UUID userId) {
        return refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
    }

    // -------------------------------------------------------------------------

    private String generatePlaintext() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
