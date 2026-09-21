package com.sudo0x.simple.identity.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
        @NotBlank(message = "Role name is required")
        @Size(min = 2, max = 64, message = "Role name must be 2-64 characters")
        @Pattern(regexp = "^[A-Z0-9_]+$", message = "Role name must be uppercase letters, digits or underscores")
        String name,

        @Size(max = 255)
        String description
) {}
