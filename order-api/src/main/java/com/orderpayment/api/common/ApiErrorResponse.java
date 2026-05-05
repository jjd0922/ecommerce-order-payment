package com.orderpayment.api.common;

import java.time.LocalDateTime;

public record ApiErrorResponse(
        String code,
        String message,
        LocalDateTime timestamp
) {

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message, LocalDateTime.now());
    }
}
