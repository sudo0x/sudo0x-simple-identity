package com.sudo0x.simple.identity.common.exception;

public class TokenExpiredException extends RuntimeException {
    public TokenExpiredException() {
        super("Token has expired");
    }
    public TokenExpiredException(String message) {
        super(message);
    }
}
