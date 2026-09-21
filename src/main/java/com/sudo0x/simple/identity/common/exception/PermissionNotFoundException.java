package com.sudo0x.simple.identity.common.exception;

import java.util.UUID;

public class PermissionNotFoundException extends RuntimeException {
    public PermissionNotFoundException(UUID id) {
        super("Permission not found: " + id);
    }
}
