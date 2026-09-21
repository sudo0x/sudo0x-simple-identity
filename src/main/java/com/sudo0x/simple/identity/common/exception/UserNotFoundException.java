package com.sudo0x.simple.identity.common.exception;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID id) {
        super("User not found: " + id);
    }

    public UserNotFoundException(String identifier) {
        super("User not found: " + identifier);
    }
}
