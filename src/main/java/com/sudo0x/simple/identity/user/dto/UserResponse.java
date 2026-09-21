package com.sudo0x.simple.identity.user.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        String status,
        boolean emailVerified,
        Set<String> roles,
        Instant createdAt,
        Instant updatedAt
) {}
