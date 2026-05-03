package com.orderpayment.domain.product;

import com.orderpayment.domain.common.DomainException;
import java.util.Objects;
import java.util.UUID;

public record ProductId(UUID value) {

    public ProductId {
        Objects.requireNonNull(value, "product id must not be null");
    }

    public static ProductId newId() {
        return new ProductId(UUID.randomUUID());
    }

    public static ProductId of(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException("product id must not be blank");
        }
        return new ProductId(UUID.fromString(value));
    }
}
