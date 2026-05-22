package com.orderpayment.application.payment.service;

import com.orderpayment.application.payment.dto.PreparePaymentResult;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.order.OrderStatus;
import com.orderpayment.domain.payment.PaymentId;
import com.orderpayment.domain.payment.PaymentStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record PreparePaymentIdempotencyResponse(
        UUID paymentId,
        UUID orderId,
        BigDecimal amount,
        OrderStatus orderStatus,
        PaymentStatus paymentStatus
) {

    static PreparePaymentIdempotencyResponse from(PreparePaymentResult result) {
        return new PreparePaymentIdempotencyResponse(
                result.paymentId().value(),
                result.orderId().value(),
                result.amount().amount(),
                result.orderStatus(),
                result.paymentStatus()
        );
    }

    PreparePaymentResult toResult() {
        return new PreparePaymentResult(
                new PaymentId(paymentId),
                new OrderId(orderId),
                new Money(amount),
                orderStatus,
                paymentStatus
        );
    }
}
