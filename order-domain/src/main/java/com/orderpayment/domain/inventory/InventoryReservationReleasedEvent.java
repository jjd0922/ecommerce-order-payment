package com.orderpayment.domain.inventory;

import com.orderpayment.domain.common.event.DomainEvent;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.product.ProductId;
import java.time.LocalDateTime;
import java.util.Objects;

public record InventoryReservationReleasedEvent(
        InventoryReservationId reservationId,
        OrderId orderId,
        ProductId productId,
        int quantity,
        LocalDateTime occurredAt
) implements DomainEvent {

    public InventoryReservationReleasedEvent {
        Objects.requireNonNull(reservationId, "reservation id must not be null");
        Objects.requireNonNull(orderId, "order id must not be null");
        Objects.requireNonNull(productId, "product id must not be null");
        Objects.requireNonNull(occurredAt, "occurred at must not be null");
    }

    @Override
    public String eventType() {
        return "InventoryReservationReleased";
    }

    @Override
    public String aggregateId() {
        return reservationId.value().toString();
    }
}
