package com.sudo0x.simple.identity.password.controller;

import com.sudo0x.simple.identity.common.response.ApiResponse;
import com.sudo0x.simple.identity.common.security.CurrentUser;
import com.sudo0x.simple.identity.common.security.SecurityPrincipal;
import com.sudo0x.simple.identity.password.service.PasswordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Password Management")
public class PasswordController {

    private final PasswordService passwordService;

    @PostMapping("/change-password")
    @Operation(summary = "Change password for the authenticated user",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @CurrentUser SecurityPrincipal principal,
            HttpServletRequest httpRequest) {
        passwordService.changePassword(
                principal.userId(),
                request.currentPassword(),
                request.newPassword(),
                getIp(httpRequest), getUserAgent(httpRequest));
        return ResponseEntity.ok(ApiResponse.ok(null, "Password changed successfully"));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset email (no auth required)")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {
        passwordService.requestPasswordReset(request.email(), getIp(httpRequest), getUserAgent(httpRequest));
        // Generic response to avoid user enumeration
        return ResponseEntity.ok(ApiResponse.ok(null,
                "If the account exists, a password reset instruction has been sent."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using a reset token (no auth required)")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {
        passwordService.resetPassword(request.token(), request.newPassword(),
                getIp(httpRequest), getUserAgent(httpRequest));
        return ResponseEntity.ok(ApiResponse.ok(null, "Password has been reset successfully"));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email address using a verification token")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request,
            HttpServletRequest httpRequest) {
        passwordService.verifyEmail(request.token(), getIp(httpRequest), getUserAgent(httpRequest));
        return ResponseEntity.ok(ApiResponse.ok(null, "Email verified successfully"));
    }

    // -------------------------------------------------------------------------

    private String getIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private String getUserAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return ua != null ? ua.substring(0, Math.min(ua.length(), 256)) : null;
    }

    // -------------------------------------------------------------------------
    // Nested request records (kept here since they're controller-specific)
    // -------------------------------------------------------------------------

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required") String currentPassword,
            @NotBlank(message = "New password is required") String newPassword
    ) {}

    public record ForgotPasswordRequest(
            @NotBlank @Email(message = "Valid email is required") String email
    ) {}

    public record ResetPasswordRequest(
            @NotBlank(message = "Reset token is required") String token,
            @NotBlank(message = "New password is required") String newPassword
    ) {}

    public record VerifyEmailRequest(
            @NotBlank(message = "Verification token is required") String token
    ) {}
}
