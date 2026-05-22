package com.orderpayment.application.payment.service;

import com.orderpayment.application.payment.dto.ConfirmPaymentResult;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.order.OrderStatus;
import com.orderpayment.domain.payment.PaymentId;
import com.orderpayment.domain.payment.PaymentStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record ConfirmPaymentIdempotencyResponse(
        UUID paymentId,
        UUID orderId,
        BigDecimal amount,
        OrderStatus orderStatus,
        PaymentStatus paymentStatus
) {

    public static ConfirmPaymentIdempotencyResponse from(ConfirmPaymentResult result) {
        return new ConfirmPaymentIdempotencyResponse(
                result.paymentId().value(),
                result.orderId().value(),
                result.amount().amount(),
                result.orderStatus(),
                result.paymentStatus()
        );
    }

    public ConfirmPaymentResult toResult() {
        return new ConfirmPaymentResult(
                new PaymentId(paymentId),
                new OrderId(orderId),
                new Money(amount),
                orderStatus,
                paymentStatus
        );
    }
}
