package com.orderpayment.application.order.port.out;

import com.orderpayment.domain.order.OrderId;

public interface OrderIdGeneratorPort {

    OrderId generate();
}
