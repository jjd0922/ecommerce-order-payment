package com.orderpayment.application.order.port.in;

import com.orderpayment.application.order.dto.CreateOrderCommand;
import com.orderpayment.application.order.dto.CreateOrderResult;

public interface CreateOrderUseCase {

    CreateOrderResult create(CreateOrderCommand command);
}
