package com.sudo0x.simple.identity.password.service;

import com.sudo0x.simple.identity.audit.entity.AuditEventType;
import com.sudo0x.simple.identity.audit.service.AuditService;
import com.sudo0x.simple.identity.common.config.AppProperties;
import com.sudo0x.simple.identity.common.exception.InvalidCredentialsException;
import com.sudo0x.simple.identity.common.exception.TokenExpiredException;
import com.sudo0x.simple.identity.credential.service.CredentialService;
import com.sudo0x.simple.identity.password.entity.EmailVerificationToken;
import com.sudo0x.simple.identity.password.entity.PasswordResetToken;
import com.sudo0x.simple.identity.password.repository.EmailVerificationTokenRepository;
import com.sudo0x.simple.identity.password.repository.PasswordResetTokenRepository;
import com.sudo0x.simple.identity.session.service.RefreshTokenService;
import com.sudo0x.simple.identity.user.entity.User;
import com.sudo0x.simple.identity.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordService {

    private static final int TOKEN_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    private final UserRepository userRepository;
    private final CredentialService credentialService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;
    private final AuditService auditService;
    private final AppProperties props;

    // -------------------------------------------------------------------------
    // Change password (authenticated)
    // -------------------------------------------------------------------------

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword,
                               String ipAddress, String userAgent) {
        if (!credentialService.verify(userId, currentPassword)) {
            throw new InvalidCredentialsException();
        }
        credentialService.updatePassword(userId, newPassword);

        // Revoke all refresh tokens after a password change for security
        refreshTokenService.revokeAllForUser(userId);
        auditService.record(userId, AuditEventType.PASSWORD_CHANGED, null, ipAddress, userAgent);
    }

    // -------------------------------------------------------------------------
    // Forgot password (unauthenticated)
    // -------------------------------------------------------------------------

    @Transactional
    public void requestPasswordReset(String email, String ipAddress, String userAgent) {
        Optional<User> userOpt = userRepository.findByEmail(email.toLowerCase());

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // Invalidate any previous reset tokens for this user
            passwordResetTokenRepository.invalidateAllByUserId(user.getId(), Instant.now());

            String plaintext = generateSecureToken();
            String hash = RefreshTokenService.sha256(plaintext);
            Instant expiresAt = Instant.now().plus(props.email().resetTokenExpiration());

            passwordResetTokenRepository.save(new PasswordResetToken(user.getId(), hash, expiresAt));
            sendResetEmailAsync(user.getEmail(), user.getUsername(), plaintext);
            auditService.record(user.getId(), AuditEventType.PASSWORD_RESET_REQUESTED, null, ipAddress, userAgent);
        }
        // Always respond the same way whether the email exists or not (prevent enumeration)
    }

    @Transactional
    public void resetPassword(String token, String newPassword, String ipAddress, String userAgent) {
        String hash = RefreshTokenService.sha256(token);
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new TokenExpiredException("Invalid or expired password reset token"));

        if (!resetToken.isValid()) {
            throw new TokenExpiredException("Password reset token has expired or already been used");
        }

        credentialService.updatePassword(resetToken.getUserId(), newPassword);

        // Mark the token as used
        resetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);

        // Revoke all sessions after password reset
        refreshTokenService.revokeAllForUser(resetToken.getUserId());

        auditService.record(resetToken.getUserId(), AuditEventType.PASSWORD_RESET_COMPLETED,
                null, ipAddress, userAgent);
    }

    // -------------------------------------------------------------------------
    // Email verification
    // -------------------------------------------------------------------------

    @Transactional
    public void sendEmailVerification(UUID userId, String ipAddress, String userAgent) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));

        if (user.isEmailVerified()) {
            return; // Already verified, no-op
        }

        String plaintext = generateSecureToken();
        String hash = RefreshTokenService.sha256(plaintext);
        Instant expiresAt = Instant.now().plus(props.email().verificationTokenExpiration());

        emailVerificationTokenRepository.save(new EmailVerificationToken(userId, hash, expiresAt));
        sendVerificationEmailAsync(user.getEmail(), user.getUsername(), plaintext);
    }

    @Transactional
    public void verifyEmail(String token, String ipAddress, String userAgent) {
        String hash = RefreshTokenService.sha256(token);
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new TokenExpiredException("Invalid or expired verification token"));

        if (!verificationToken.isValid()) {
            throw new TokenExpiredException("Verification token has expired or already been used");
        }

        User user = userRepository.findById(verificationToken.getUserId())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        user.setEmailVerified(true);
        userRepository.save(user);

        verificationToken.setUsedAt(Instant.now());
        emailVerificationTokenRepository.save(verificationToken);

        auditService.record(user.getId(), AuditEventType.EMAIL_VERIFIED, null, ipAddress, userAgent);
    }

    // -------------------------------------------------------------------------

    private String generateSecureToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void sendResetEmailAsync(String email, String username, String token) {
        try {
            emailService.sendPasswordResetEmail(email, username, token);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}", email, e);
        }
    }

    private void sendVerificationEmailAsync(String email, String username, String token) {
        try {
            emailService.sendEmailVerificationEmail(email, username, token);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}", email, e);
        }
    }
}
