package com.sudo0x.simple.identity.common.exception;

import com.sudo0x.simple.identity.common.response.ApiResponse;
import com.sudo0x.simple.identity.common.response.FieldError;
import com.sudo0x.simple.identity.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldError> errors = extractFieldErrors(ex.getBindingResult());
        return respond(HttpStatus.BAD_REQUEST, "Validation failed", errors, request);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserNotFound(
            UserNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ex.getMessage(), null, request);
    }

    @ExceptionHandler(RoleNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleRoleNotFound(
            RoleNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ex.getMessage(), null, request);
    }

    @ExceptionHandler(PermissionNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handlePermissionNotFound(
            PermissionNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ex.getMessage(), null, request);
    }

    @ExceptionHandler({
        UsernameAlreadyExistsException.class,
        EmailAlreadyExistsException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleConflict(
            RuntimeException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, ex.getMessage(), null, request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest request) {
        // Generic message to avoid user enumeration
        return respond(HttpStatus.UNAUTHORIZED, "Authentication failed", null, request);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountLocked(
            AccountLockedException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, ex.getMessage(), null, request);
    }

    @ExceptionHandler(AccountDisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountDisabled(
            AccountDisabledException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, "Authentication failed", null, request);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRefreshToken(
            InvalidRefreshTokenException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, ex.getMessage(), null, request);
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleTokenExpired(
            TokenExpiredException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, ex.getMessage(), null, request);
    }

    @ExceptionHandler(PasswordPolicyException.class)
    public ResponseEntity<ApiResponse<Void>> handlePasswordPolicy(
            PasswordPolicyException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, ex.getMessage(), null, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, "Access denied", null, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", null, request);
    }

    // -------------------------------------------------------------------------

    private ResponseEntity<ApiResponse<Void>> respond(
            HttpStatus status, String message,
            List<FieldError> errors, HttpServletRequest request) {
        String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR);
        ApiResponse<Void> body = ApiResponse.<Void>error(message, errors)
                .withRequestId(requestId);
        return ResponseEntity.status(status).body(body);
    }

    private List<FieldError> extractFieldErrors(BindingResult result) {
        return result.getFieldErrors().stream()
                .map(fe -> new FieldError(
                        fe.getField(),
                        fe.getCode(),
                        fe.getDefaultMessage()))
                .toList();
    }
}
