package com.orderpayment.domain.payment;

import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.order.OrderId;
import java.util.Objects;

public class Payment {

    private final PaymentId id;
    private final OrderId orderId;
    private final Money amount;
    private final IdempotencyKey idempotencyKey;
    private PaymentStatus status;

    private Payment(
            PaymentId id,
            OrderId orderId,
            Money amount,
            IdempotencyKey idempotencyKey,
            PaymentStatus status
    ) {
        this.id = Objects.requireNonNull(id, "payment id must not be null");
        this.orderId = Objects.requireNonNull(orderId, "order id must not be null");
        this.amount = Objects.requireNonNull(amount, "payment amount must not be null");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotency key must not be null");
        this.status = Objects.requireNonNull(status, "payment status must not be null");
    }

    public static Payment ready(PaymentId id, OrderId orderId, Money amount, IdempotencyKey idempotencyKey) {
        return new Payment(id, orderId, amount, idempotencyKey, PaymentStatus.READY);
    }

    public static Payment restore(
            PaymentId id,
            OrderId orderId,
            Money amount,
            IdempotencyKey idempotencyKey,
            PaymentStatus status
    ) {
        return new Payment(id, orderId, amount, idempotencyKey, status);
    }

    public PaymentId id() {
        return id;
    }

    public OrderId orderId() {
        return orderId;
    }

    public Money amount() {
        return amount;
    }

    public IdempotencyKey idempotencyKey() {
        return idempotencyKey;
    }

    public PaymentStatus status() {
        return status;
    }

    public void approve() {
        ensureStatus(PaymentStatus.READY);
        status = PaymentStatus.APPROVED;
    }

    public void fail() {
        ensureStatus(PaymentStatus.READY);
        status = PaymentStatus.FAILED;
    }

    public void cancel() {
        if (status == PaymentStatus.APPROVED) {
            throw new DomainException("approved payment cannot be cancelled");
        }
        status = PaymentStatus.CANCELLED;
    }

    private void ensureStatus(PaymentStatus expectedStatus) {
        if (status != expectedStatus) {
            throw new DomainException("invalid payment status transition");
        }
    }
}
