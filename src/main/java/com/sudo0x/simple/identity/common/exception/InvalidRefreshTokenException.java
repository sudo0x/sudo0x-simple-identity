package com.sudo0x.simple.identity.common.exception;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("Invalid or expired refresh token");
    }
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
