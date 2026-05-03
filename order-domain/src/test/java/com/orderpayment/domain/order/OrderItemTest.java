package com.orderpayment.domain.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.product.ProductId;
import org.junit.jupiter.api.Test;

class OrderItemTest {

    @Test
    void calculatesSubtotal() {
        OrderItem item = OrderItem.of(ProductId.newId(), "keyboard", Money.won(1000), 3);

        assertEquals(Money.won(3000), item.subtotal());
    }

    @Test
    void rejectsInvalidQuantity() {
        assertThrows(DomainException.class, () -> OrderItem.of(ProductId.newId(), "keyboard", Money.won(1000), 0));
    }
}
