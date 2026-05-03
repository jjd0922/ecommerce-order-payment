package com.orderpayment.application.order.dto;

import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.order.OrderStatus;

public record CreateOrderResult(
        OrderId orderId,
        Money totalAmount,
        OrderStatus status
) {
}
