package com.sudo0x.simple.identity.authentication.dto;

import java.util.Set;
import java.util.UUID;

public record MeResponse(
        UUID id,
        String username,
        String email,
        String status,
        boolean emailVerified,
        Set<String> roles,
        Set<String> permissions
) {}
