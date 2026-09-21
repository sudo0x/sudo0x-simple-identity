package com.sudo0x.simple.identity.authentication.dto;

public record RefreshRequest(
        // Optional when cookie.enabled=true — the controller reads the token from the
        // refresh_token HttpOnly cookie if this field is null/blank.
        String refreshToken
) {}
