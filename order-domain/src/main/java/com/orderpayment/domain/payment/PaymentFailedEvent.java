package com.orderpayment.domain.payment;

import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.common.event.DomainEvent;
import com.orderpayment.domain.order.OrderId;
import java.time.LocalDateTime;
import java.util.Objects;

public record PaymentFailedEvent(
        PaymentId paymentId,
        OrderId orderId,
        Money amount,
        String reason,
        LocalDateTime occurredAt
) implements DomainEvent {

    public PaymentFailedEvent {
        Objects.requireNonNull(paymentId, "payment id must not be null");
        Objects.requireNonNull(orderId, "order id must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(occurredAt, "occurred at must not be null");
    }

    @Override
    public String eventType() {
        return "PaymentFailed";
    }

    @Override
    public String aggregateId() {
        return paymentId.value().toString();
    }
}
