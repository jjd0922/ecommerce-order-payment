package com.orderpayment.application.payment.dto;

import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.order.OrderStatus;
import com.orderpayment.domain.payment.PaymentId;
import com.orderpayment.domain.payment.PaymentStatus;

public record ConfirmPaymentResult(
        PaymentId paymentId,
        OrderId orderId,
        Money amount,
        OrderStatus orderStatus,
        PaymentStatus paymentStatus
) {
}
