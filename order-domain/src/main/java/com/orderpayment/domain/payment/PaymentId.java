package com.orderpayment.domain.payment;

import com.orderpayment.domain.common.DomainException;
import java.util.Objects;
import java.util.UUID;

public record PaymentId(UUID value) {

    public PaymentId {
        Objects.requireNonNull(value, "payment id must not be null");
    }

    public static PaymentId newId() {
        return new PaymentId(UUID.randomUUID());
    }

    public static PaymentId of(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException("payment id must not be blank");
        }
        return new PaymentId(UUID.fromString(value));
    }
}
