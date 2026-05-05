package com.orderpayment.infrastructure.order;

import com.orderpayment.application.order.port.out.OrderIdGeneratorPort;
import com.orderpayment.domain.order.OrderId;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UuidOrderIdGeneratorAdapter implements OrderIdGeneratorPort {

    @Override
    public OrderId generate() {
        return new OrderId(UUID.randomUUID());
    }
}
