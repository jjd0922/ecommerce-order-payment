package com.orderpayment.domain.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.product.ProductId;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderTest {

    @Test
    void createsOrderAndCalculatesTotalAmount() {
        Order order = Order.create(OrderId.newId(), List.of(
                OrderItem.of(ProductId.newId(), "keyboard", Money.won(1000), 2),
                OrderItem.of(ProductId.newId(), "mouse", Money.won(500), 1)
        ));

        assertEquals(OrderStatus.CREATED, order.status());
        assertEquals(Money.won(2500), order.totalAmount());
    }

    @Test
    void changesStatusFromCreatedToPaid() {
        Order order = order();

        order.requestPayment();
        order.markPaid();

        assertEquals(OrderStatus.PAID, order.status());
    }

    @Test
    void rejectsInvalidStatusTransition() {
        Order order = order();

        assertThrows(DomainException.class, order::markPaid);
    }

    @Test
    void rejectsPaidOrderCancellation() {
        Order order = order();
        order.requestPayment();
        order.markPaid();

        assertThrows(DomainException.class, order::cancel);
    }

    @Test
    void rejectsEmptyItems() {
        assertThrows(DomainException.class, () -> Order.create(OrderId.newId(), List.of()));
    }

    private static Order order() {
        return Order.create(OrderId.newId(), List.of(
                OrderItem.of(ProductId.newId(), "keyboard", Money.won(1000), 1)
        ));
    }
}
