package com.orderpayment.domain.payment;

import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.common.event.DomainEvent;
import com.orderpayment.domain.order.OrderId;
import java.time.LocalDateTime;

public record PaymentPreparedEvent(
        PaymentId paymentId,
        OrderId orderId,
        Money amount,
        IdempotencyKey idempotencyKey,
        LocalDateTime occurredAt
) implements DomainEvent {

    @Override
    public String eventType() {
        return "PaymentPrepared";
    }

    @Override
    public String aggregateId() {
        return paymentId.value().toString();
    }
}
