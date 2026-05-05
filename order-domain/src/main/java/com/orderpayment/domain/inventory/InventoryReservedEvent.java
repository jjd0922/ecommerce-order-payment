package com.orderpayment.domain.inventory;

import com.orderpayment.domain.common.event.DomainEvent;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.product.ProductId;
import java.time.LocalDateTime;

public record InventoryReservedEvent(
        InventoryReservationId reservationId,
        OrderId orderId,
        ProductId productId,
        int quantity,
        LocalDateTime expiresAt,
        LocalDateTime occurredAt
) implements DomainEvent {

    @Override
    public String eventType() {
        return "InventoryReserved";
    }

    @Override
    public String aggregateId() {
        return reservationId.value().toString();
    }
}
