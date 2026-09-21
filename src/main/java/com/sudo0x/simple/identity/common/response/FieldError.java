package com.sudo0x.simple.identity.common.response;

public record FieldError(
        String field,
        String code,
        String message
) {}
