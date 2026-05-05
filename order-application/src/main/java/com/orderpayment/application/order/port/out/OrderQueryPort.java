package com.orderpayment.application.order.port.out;

import com.orderpayment.domain.order.Order;
import com.orderpayment.domain.order.OrderId;

public interface OrderQueryPort {

    Order getOrder(OrderId orderId);
}
