package com.sudo0x.simple.identity.role.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateRoleRequest(
        @Size(min = 2, max = 64)
        @Pattern(regexp = "^[A-Z0-9_]+$", message = "Role name must be uppercase letters, digits or underscores")
        String name,

        @Size(max = 255)
        String description
) {}
