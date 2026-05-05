package com.orderpayment.application.payment.port.out;

import com.orderpayment.domain.inventory.InventoryReservation;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.product.ProductId;
import java.time.LocalDateTime;

public interface InventoryReservationCommandPort {

    InventoryReservation hold(OrderId orderId, ProductId productId, int quantity, LocalDateTime expiresAt);
}
