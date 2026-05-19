package com.orderpayment.api.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "invalid request"),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "idempotency key conflict"),
    IDEMPOTENCY_IN_FLIGHT(HttpStatus.CONFLICT, "idempotency request in flight"),
    DOMAIN_RULE_VIOLATION(HttpStatus.CONFLICT, "domain rule violation"),
    UNPROCESSABLE_ENTITY(HttpStatus.UNPROCESSABLE_ENTITY, "unprocessable entity"),
    INFRASTRUCTURE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "infrastructure error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
