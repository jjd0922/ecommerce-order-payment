package com.orderpayment.domain.order;

import com.orderpayment.domain.common.DomainException;
import java.util.Objects;
import java.util.UUID;

public record OrderId(UUID value) {

    public OrderId {
        Objects.requireNonNull(value, "order id must not be null");
    }

    public static OrderId newId() {
        return new OrderId(UUID.randomUUID());
    }

    public static OrderId of(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException("order id must not be blank");
        }
        return new OrderId(UUID.fromString(value));
    }
}
