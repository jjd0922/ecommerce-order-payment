package com.orderpayment.domain.payment;

import com.orderpayment.domain.common.DomainException;

public record IdempotencyKey(String value) {

    private static final int MAX_LENGTH = 100;

    public IdempotencyKey {
        if (value == null || value.isBlank()) {
            throw new DomainException("idempotency key must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new DomainException("idempotency key is too long");
        }
    }
}
