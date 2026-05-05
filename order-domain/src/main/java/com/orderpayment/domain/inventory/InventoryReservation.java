package com.orderpayment.domain.inventory;

import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.product.ProductId;
import java.time.LocalDateTime;
import java.util.Objects;

public class InventoryReservation {

    private final InventoryReservationId id;
    private final OrderId orderId;
    private final ProductId productId;
    private final int quantity;
    private final LocalDateTime expiresAt;
    private InventoryReservationStatus status;

    private InventoryReservation(
            InventoryReservationId id,
            OrderId orderId,
            ProductId productId,
            int quantity,
            LocalDateTime expiresAt,
            InventoryReservationStatus status
    ) {
        this.id = Objects.requireNonNull(id, "reservation id must not be null");
        this.orderId = Objects.requireNonNull(orderId, "order id must not be null");
        this.productId = Objects.requireNonNull(productId, "product id must not be null");
        this.quantity = validateQuantity(quantity);
        this.expiresAt = Objects.requireNonNull(expiresAt, "expires at must not be null");
        this.status = Objects.requireNonNull(status, "reservation status must not be null");
    }

    public static InventoryReservation hold(
            InventoryReservationId id,
            OrderId orderId,
            ProductId productId,
            int quantity,
            LocalDateTime expiresAt
    ) {
        return new InventoryReservation(id, orderId, productId, quantity, expiresAt, InventoryReservationStatus.HELD);
    }

    public static InventoryReservation restore(
            InventoryReservationId id,
            OrderId orderId,
            ProductId productId,
            int quantity,
            LocalDateTime expiresAt,
            InventoryReservationStatus status
    ) {
        return new InventoryReservation(id, orderId, productId, quantity, expiresAt, status);
    }

    public InventoryReservationId id() {
        return id;
    }

    public OrderId orderId() {
        return orderId;
    }

    public ProductId productId() {
        return productId;
    }

    public int quantity() {
        return quantity;
    }

    public LocalDateTime expiresAt() {
        return expiresAt;
    }

    public InventoryReservationStatus status() {
        return status;
    }

    public void release() {
        ensureHeld();
        status = InventoryReservationStatus.RELEASED;
    }

    public void expire(LocalDateTime now) {
        ensureHeld();
        if (now.isBefore(expiresAt)) {
            throw new DomainException("reservation is not expired");
        }
        status = InventoryReservationStatus.EXPIRED;
    }

    public void confirm() {
        ensureHeld();
        status = InventoryReservationStatus.CONFIRMED;
    }

    private void ensureHeld() {
        if (status != InventoryReservationStatus.HELD) {
            throw new DomainException("reservation is not held");
        }
    }

    private static int validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new DomainException("reservation quantity must be positive");
        }
        return quantity;
    }
}
