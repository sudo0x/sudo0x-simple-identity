package com.sudo0x.simple.identity.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        Instant timestamp,
        String requestId,
        List<FieldError> errors
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now(), null, null);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, Instant.now(), null, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, Instant.now(), null, null);
    }

    public static <T> ApiResponse<T> error(String message, List<FieldError> errors) {
        return new ApiResponse<>(false, null, message, Instant.now(), null, errors);
    }

    public ApiResponse<T> withRequestId(String requestId) {
        return new ApiResponse<>(success, data, message, timestamp, requestId, errors);
    }
}
