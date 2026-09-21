package com.sudo0x.simple.identity.authentication.service;

import com.sudo0x.simple.identity.audit.entity.AuditEventType;
import com.sudo0x.simple.identity.audit.service.AuditService;
import com.sudo0x.simple.identity.authentication.dto.LoginRequest;
import com.sudo0x.simple.identity.authentication.dto.LoginResponse;
import com.sudo0x.simple.identity.authentication.dto.MeResponse;
import com.sudo0x.simple.identity.common.config.AppProperties;
import com.sudo0x.simple.identity.common.exception.*;
import com.sudo0x.simple.identity.common.security.SecurityPrincipal;
import com.sudo0x.simple.identity.credential.service.CredentialService;
import com.sudo0x.simple.identity.permission.entity.Permission;
import com.sudo0x.simple.identity.role.entity.Role;
import com.sudo0x.simple.identity.session.service.RefreshTokenService;
import com.sudo0x.simple.identity.user.entity.User;
import com.sudo0x.simple.identity.user.entity.UserStatus;
import com.sudo0x.simple.identity.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final CredentialService credentialService;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final AppProperties props;

    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
        User user = userRepository.findByUsernameWithRolesAndPermissions(request.username())
                .orElse(null);

        if (user == null) {
            // Perform dummy password check to prevent timing attacks that reveal username existence
            credentialService.verify(UUID.randomUUID(), request.password());
            auditService.record(AuditEventType.LOGIN_FAILURE,
                    "Unknown username: " + request.username(), ipAddress, userAgent);
            throw new InvalidCredentialsException();
        }

        // Check account status before verifying password to fail fast
        checkAccountStatus(user, ipAddress, userAgent);

        boolean passwordValid = credentialService.verify(user.getId(), request.password());

        if (!passwordValid) {
            handleFailedAttempt(user, ipAddress, userAgent);
            throw new InvalidCredentialsException();
        }

        // Successful login — reset failed attempts
        if (user.getFailedLoginAttempts() > 0 || user.getStatus() == UserStatus.LOCKED) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            if (user.getStatus() == UserStatus.LOCKED) {
                user.setStatus(UserStatus.ACTIVE);
            }
            userRepository.save(user);
        }

        SecurityPrincipal principal = buildPrincipal(user);
        String accessToken = jwtService.generateAccessToken(principal);
        String refreshToken = refreshTokenService.issue(user.getId());

        auditService.record(user.getId(), AuditEventType.LOGIN_SUCCESS, null, ipAddress, userAgent);
        log.debug("User {} logged in successfully", user.getUsername());

        long expiresIn = props.jwt().accessTokenExpiration().toSeconds();
        return LoginResponse.of(accessToken, refreshToken, expiresIn);
    }

    @Transactional
    public LoginResponse refresh(String plaintextToken, String ipAddress, String userAgent) {
        // rotate() handles reuse detection and revocation atomically
        String newPlaintextToken = refreshTokenService.rotate(plaintextToken);

        // Get userId after rotation (the new token belongs to the same user)
        UUID userId = refreshTokenService.getUserIdFromToken(newPlaintextToken);
        User user = userRepository.findByIdWithRolesAndPermissions(userId)
                .orElseThrow(InvalidRefreshTokenException::new);

        checkAccountStatus(user, ipAddress, userAgent);

        SecurityPrincipal principal = buildPrincipal(user);
        String accessToken = jwtService.generateAccessToken(principal);

        auditService.record(userId, AuditEventType.TOKEN_REFRESHED, null, ipAddress, userAgent);

        long expiresIn = props.jwt().accessTokenExpiration().toSeconds();
        return LoginResponse.of(accessToken, newPlaintextToken, expiresIn);
    }

    @Transactional
    public void logout(String plaintextToken, UUID userId, String ipAddress, String userAgent) {
        refreshTokenService.revoke(plaintextToken);
        auditService.record(userId, AuditEventType.LOGOUT, null, ipAddress, userAgent);
    }

    @Transactional
    public void logoutAll(UUID userId, String ipAddress, String userAgent) {
        refreshTokenService.revokeAllForUser(userId);
        auditService.record(userId, AuditEventType.LOGOUT_ALL, null, ipAddress, userAgent);
    }

    @Transactional(readOnly = true)
    public MeResponse getMe(UUID userId) {
        User user = userRepository.findByIdWithRolesAndPermissions(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        Set<String> permissions = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getCode)
                .collect(Collectors.toSet());

        return new MeResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getStatus().name(),
                user.isEmailVerified(),
                roles,
                permissions
        );
    }

    // -------------------------------------------------------------------------

    private void checkAccountStatus(User user, String ipAddress, String userAgent) {
        if (user.getStatus() == UserStatus.DELETED || user.getStatus() == UserStatus.INACTIVE) {
            throw new AccountDisabledException();
        }
        if (user.getStatus() == UserStatus.LOCKED) {
            // Check if lock duration has expired
            if (user.getLockedUntil() != null && Instant.now().isBefore(user.getLockedUntil())) {
                throw new AccountLockedException();
            }
            // Lock has expired — unlock the account
            user.setStatus(UserStatus.ACTIVE);
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
            auditService.record(user.getId(), AuditEventType.ACCOUNT_UNLOCKED,
                    "Lock expired — account auto-unlocked", ipAddress, userAgent);
        }
    }

    private void handleFailedAttempt(User user, String ipAddress, String userAgent) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        int maxAttempts = props.security().maxLoginAttempts();
        if (attempts >= maxAttempts) {
            user.setStatus(UserStatus.LOCKED);
            user.setLockedUntil(Instant.now().plus(props.security().lockDuration()));
            userRepository.save(user);
            auditService.record(user.getId(), AuditEventType.ACCOUNT_LOCKED,
                    "Locked after " + attempts + " failed attempts", ipAddress, userAgent);
        } else {
            userRepository.save(user);
            auditService.record(user.getId(), AuditEventType.LOGIN_FAILURE,
                    "Failed attempt " + attempts + " of " + maxAttempts, ipAddress, userAgent);
        }
    }

    public SecurityPrincipal buildPrincipal(User user) {
        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        Set<String> permissions = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getCode)
                .collect(Collectors.toSet());
        return new SecurityPrincipal(user.getId(), user.getUsername(), roles, permissions);
    }
}
