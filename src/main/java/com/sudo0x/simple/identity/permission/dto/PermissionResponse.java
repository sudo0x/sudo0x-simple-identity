package com.sudo0x.simple.identity.permission.dto;

import java.time.Instant;
import java.util.UUID;

public record PermissionResponse(
        UUID id,
        String code,
        String description,
        Instant createdAt
) {}
