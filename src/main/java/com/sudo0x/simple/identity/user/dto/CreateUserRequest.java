package com.sudo0x.simple.identity.user.dto;

import jakarta.validation.constraints.*;

public record CreateUserRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 64, message = "Username must be 3-64 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$",
                message = "Username may only contain letters, digits, underscores, dots and hyphens")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email address")
        @Size(max = 255)
        String email,

        @NotBlank(message = "Password is required")
        String password
) {}
