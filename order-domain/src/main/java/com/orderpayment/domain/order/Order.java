package com.orderpayment.domain.order;

import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.common.Money;
import java.util.List;
import java.util.Objects;

public class Order {

    private final OrderId id;
    private final List<OrderItem> items;
    private final Money totalAmount;
    private OrderStatus status;

    private Order(OrderId id, List<OrderItem> items, OrderStatus status) {
        this.id = Objects.requireNonNull(id, "order id must not be null");
        this.items = List.copyOf(validateItems(items));
        this.totalAmount = calculateTotalAmount(this.items);
        this.status = Objects.requireNonNull(status, "order status must not be null");
    }

    public static Order create(OrderId id, List<OrderItem> items) {
        return new Order(id, items, OrderStatus.CREATED);
    }

    public OrderId id() {
        return id;
    }

    public List<OrderItem> items() {
        return items;
    }

    public Money totalAmount() {
        return totalAmount;
    }

    public OrderStatus status() {
        return status;
    }

    public void requestPayment() {
        ensureStatus(OrderStatus.CREATED);
        status = OrderStatus.PAYMENT_PENDING;
    }

    public void markPaid() {
        ensureStatus(OrderStatus.PAYMENT_PENDING);
        status = OrderStatus.PAID;
    }

    public void markFailed() {
        if (status == OrderStatus.PAID || status == OrderStatus.CANCELLED) {
            throw new DomainException("paid or cancelled order cannot fail");
        }
        status = OrderStatus.FAILED;
    }

    public void cancel() {
        if (status == OrderStatus.PAID) {
            throw new DomainException("paid order cannot be cancelled");
        }
        status = OrderStatus.CANCELLED;
    }

    private void ensureStatus(OrderStatus expectedStatus) {
        if (status != expectedStatus) {
            throw new DomainException("invalid order status transition");
        }
    }

    private static List<OrderItem> validateItems(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new DomainException("order must have at least one item");
        }
        return items;
    }

    private static Money calculateTotalAmount(List<OrderItem> items) {
        return items.stream()
                .map(OrderItem::subtotal)
                .reduce(Money.ZERO, Money::add);
    }
}
