package com.orderpayment.application.order.dto;

import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.product.ProductId;
import java.util.List;
import java.util.Objects;

public record CreateOrderCommand(List<OrderLine> orderLines) {

    public CreateOrderCommand {
        if (orderLines == null || orderLines.isEmpty()) {
            throw new DomainException("order command must have at least one line");
        }
        orderLines = List.copyOf(orderLines);
    }

    public record OrderLine(ProductId productId, int quantity) {

        public OrderLine {
            Objects.requireNonNull(productId, "product id must not be null");
            if (quantity <= 0) {
                throw new DomainException("order line quantity must be positive");
            }
        }
    }
}
