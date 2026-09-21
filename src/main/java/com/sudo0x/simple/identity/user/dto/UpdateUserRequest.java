package com.sudo0x.simple.identity.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(min = 3, max = 64, message = "Username must be 3-64 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$",
                message = "Username may only contain letters, digits, underscores, dots and hyphens")
        String username,

        @Email(message = "Invalid email address")
        @Size(max = 255)
        String email,

        String status
) {}
