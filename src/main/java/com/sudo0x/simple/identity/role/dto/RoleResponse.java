package com.sudo0x.simple.identity.role.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String name,
        String description,
        Set<String> permissions,
        Instant createdAt,
        Instant updatedAt
) {}
