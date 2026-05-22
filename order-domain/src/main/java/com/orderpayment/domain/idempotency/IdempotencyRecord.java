package com.orderpayment.domain.idempotency;

import com.orderpayment.domain.common.DomainException;
import java.time.LocalDateTime;
import java.util.Objects;

public class IdempotencyRecord {

    private final String key;
    private final String requestHash;
    private String responseBody;
    private IdempotencyRecordStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime expiresAt;

    private IdempotencyRecord(
            String key,
            String requestHash,
            String responseBody,
            IdempotencyRecordStatus status,
            LocalDateTime createdAt,
            LocalDateTime expiresAt
    ) {
        this.key = requireText(key, "idempotency key must not be blank");
        this.requestHash = requireText(requestHash, "request hash must not be blank");
        this.responseBody = responseBody;
        this.status = Objects.requireNonNull(status, "idempotency status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "created at must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expires at must not be null");
        if (!expiresAt.isAfter(createdAt)) {
            throw new DomainException("idempotency record expiresAt must be after createdAt");
        }
    }

    public static IdempotencyRecord inFlight(
            String key,
            String requestHash,
            LocalDateTime createdAt,
            LocalDateTime expiresAt
    ) {
        return new IdempotencyRecord(key, requestHash, null, IdempotencyRecordStatus.IN_FLIGHT, createdAt, expiresAt);
    }

    public static IdempotencyRecord restore(
            String key,
            String requestHash,
            String responseBody,
            IdempotencyRecordStatus status,
            LocalDateTime createdAt,
            LocalDateTime expiresAt
    ) {
        return new IdempotencyRecord(key, requestHash, responseBody, status, createdAt, expiresAt);
    }

    public String key() {
        return key;
    }

    public String requestHash() {
        return requestHash;
    }

    public String responseBody() {
        return responseBody;
    }

    public IdempotencyRecordStatus status() {
        return status;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public LocalDateTime expiresAt() {
        return expiresAt;
    }

    public boolean hasSameRequestHash(String requestHash) {
        return this.requestHash.equals(requestHash);
    }

    public void complete(String responseBody) {
        if (status != IdempotencyRecordStatus.IN_FLIGHT) {
            throw new DomainException("idempotency record is not in flight");
        }
        this.responseBody = requireText(responseBody, "response body must not be blank");
        this.status = IdempotencyRecordStatus.COMPLETED;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new DomainException(message);
        }
        return value;
    }
}
