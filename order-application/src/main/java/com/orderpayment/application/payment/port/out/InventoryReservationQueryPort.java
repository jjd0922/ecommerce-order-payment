package com.orderpayment.application.payment.port.out;

import com.orderpayment.domain.inventory.InventoryReservation;
import com.orderpayment.domain.order.OrderId;
import java.util.List;

public interface InventoryReservationQueryPort {

    List<InventoryReservation> findHeldReservationsByOrderId(OrderId orderId);
}
