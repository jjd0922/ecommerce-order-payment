package com.orderpayment.domain.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.product.ProductId;
import org.junit.jupiter.api.Test;

class InventoryTest {

    @Test
    void deductsQuantity() {
        Inventory inventory = Inventory.of(ProductId.newId(), 10);

        inventory.deduct(3);

        assertEquals(7, inventory.quantity());
    }

    @Test
    void rejectsInsufficientQuantity() {
        Inventory inventory = Inventory.of(ProductId.newId(), 2);

        assertThrows(DomainException.class, () -> inventory.deduct(3));
    }

    @Test
    void rejectsInvalidQuantity() {
        assertThrows(DomainException.class, () -> Inventory.of(ProductId.newId(), -1));
        assertThrows(DomainException.class, () -> Inventory.of(ProductId.newId(), 1).deduct(0));
    }
}
