package com.sudo0x.simple.identity.common.security;

import java.util.Set;
import java.util.UUID;

public record SecurityPrincipal(
        UUID userId,
        String username,
        Set<String> roles,
        Set<String> permissions
) {}
