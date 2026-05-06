package com.orderpayment.api.common;

import java.time.LocalDateTime;

public record ApiErrorResponse(
        String code,
        String message,
        String requestId,
        LocalDateTime timestamp
) {

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message, RequestTracing.currentRequestId(), LocalDateTime.now());
    }
}
