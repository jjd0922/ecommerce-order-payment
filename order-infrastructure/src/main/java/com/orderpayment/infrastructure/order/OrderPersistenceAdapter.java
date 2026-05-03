package com.orderpayment.infrastructure.order;

import com.orderpayment.application.order.port.out.OrderCommandPort;
import com.orderpayment.application.order.port.out.OrderQueryPort;
import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.order.Order;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.order.OrderItem;
import com.orderpayment.domain.product.ProductId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OrderPersistenceAdapter implements OrderQueryPort, OrderCommandPort {

    private final OrderJpaRepository orderJpaRepository;

    @Override
    @Transactional(readOnly = true)
    public Order getOrder(OrderId orderId) {
        return orderJpaRepository.findById(orderId.value().toString())
                .map(this::toDomain)
                .orElseThrow(() -> new DomainException("order not found"));
    }

    @Override
    @Transactional
    public void saveOrder(Order order) {
        orderJpaRepository.deleteById(order.id().value().toString());
        orderJpaRepository.flush();
        orderJpaRepository.save(toEntity(order));
    }

    private Order toDomain(OrderJpaEntity entity) {
        List<OrderItem> items = entity.items().stream()
                .map(item -> OrderItem.of(
                        new ProductId(UUID.fromString(item.productId())),
                        item.productName(),
                        new Money(item.unitPrice()),
                        item.quantity()
                ))
                .toList();
        return Order.restore(
                new OrderId(UUID.fromString(entity.id())),
                items,
                entity.status()
        );
    }

    private OrderJpaEntity toEntity(Order order) {
        OrderJpaEntity entity = new OrderJpaEntity(
                order.id().value().toString(),
                order.totalAmount().amount(),
                order.status()
        );
        for (OrderItem item : order.items()) {
            entity.addItem(new OrderItemJpaEntity(
                    item.productId().value().toString(),
                    item.productName(),
                    item.unitPrice().amount(),
                    item.quantity()
            ));
        }
        return entity;
    }
}
