package com.orderpayment.application.order.service;

import com.orderpayment.application.order.dto.CreateOrderCommand;
import com.orderpayment.application.order.dto.CreateOrderResult;
import com.orderpayment.application.order.port.in.CreateOrderUseCase;
import com.orderpayment.application.order.port.out.OrderCommandPort;
import com.orderpayment.application.order.port.out.OrderIdGeneratorPort;
import com.orderpayment.application.order.port.out.ProductQueryPort;
import com.orderpayment.domain.order.Order;
import com.orderpayment.domain.order.OrderItem;
import com.orderpayment.domain.product.Product;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateOrderService implements CreateOrderUseCase {

    private final ProductQueryPort productQueryPort;
    private final OrderCommandPort orderCommandPort;
    private final OrderIdGeneratorPort orderIdGeneratorPort;

    @Override
    @Transactional
    public CreateOrderResult create(CreateOrderCommand command) {
        List<OrderItem> orderItems = new ArrayList<>();

        for (CreateOrderCommand.OrderLine orderLine : command.orderLines()) {
            Product product = productQueryPort.getProduct(orderLine.productId());
            product.ensureSelling();

            orderItems.add(OrderItem.of(product.id(), product.name(), product.price(), orderLine.quantity()));
        }

        Order order = Order.create(orderIdGeneratorPort.generate(), orderItems);
        orderCommandPort.saveOrder(order);

        return new CreateOrderResult(order.id(), order.totalAmount(), order.status());
    }
}
