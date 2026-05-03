package com.orderpayment.application.payment.dto;

import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.payment.IdempotencyKey;
import java.util.Objects;

public record PreparePaymentCommand(
        OrderId orderId,
        IdempotencyKey idempotencyKey
) {

    public PreparePaymentCommand {
        Objects.requireNonNull(orderId, "order id must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotency key must not be null");
    }
}
