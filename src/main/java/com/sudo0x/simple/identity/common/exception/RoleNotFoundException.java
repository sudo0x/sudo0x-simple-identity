package com.sudo0x.simple.identity.common.exception;

import java.util.UUID;

public class RoleNotFoundException extends RuntimeException {
    public RoleNotFoundException(UUID id) {
        super("Role not found: " + id);
    }
    public RoleNotFoundException(String name) {
        super("Role not found: " + name);
    }
}
