package com.orderpayment.application.order.port.out;

import com.orderpayment.domain.order.Order;

public interface OrderCommandPort {

    void saveOrder(Order order);
}
