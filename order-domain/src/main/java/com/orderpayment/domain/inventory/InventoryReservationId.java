package com.orderpayment.domain.inventory;

import com.orderpayment.domain.common.DomainException;
import java.util.Objects;
import java.util.UUID;

public record InventoryReservationId(UUID value) {

    public InventoryReservationId {
        Objects.requireNonNull(value, "reservation id must not be null");
    }

    public static InventoryReservationId newId() {
        return new InventoryReservationId(UUID.randomUUID());
    }

    public static InventoryReservationId of(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException("reservation id must not be blank");
        }
        return new InventoryReservationId(UUID.fromString(value));
    }
}
