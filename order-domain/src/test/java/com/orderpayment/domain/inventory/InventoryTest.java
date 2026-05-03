package com.orderpayment.domain.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.product.ProductId;
import org.junit.jupiter.api.Test;

class InventoryTest {

    @Test
    void holdsQuantity() {
        Inventory inventory = Inventory.of(ProductId.newId(), 10);

        inventory.hold(3);

        assertEquals(7, inventory.availableQuantity());
        assertEquals(3, inventory.heldQuantity());
    }

    @Test
    void releasesHeldQuantity() {
        Inventory inventory = Inventory.of(ProductId.newId(), 10);
        inventory.hold(3);

        inventory.release(2);

        assertEquals(9, inventory.availableQuantity());
        assertEquals(1, inventory.heldQuantity());
    }

    @Test
    void confirmsHeldQuantity() {
        Inventory inventory = Inventory.of(ProductId.newId(), 10);
        inventory.hold(3);

        inventory.confirm(3);

        assertEquals(7, inventory.availableQuantity());
        assertEquals(0, inventory.heldQuantity());
    }

    @Test
    void rejectsInsufficientQuantity() {
        Inventory inventory = Inventory.of(ProductId.newId(), 2);

        assertThrows(DomainException.class, () -> inventory.deduct(3));
    }

    @Test
    void rejectsInvalidQuantity() {
        assertThrows(DomainException.class, () -> Inventory.of(ProductId.newId(), -1));
        assertThrows(DomainException.class, () -> Inventory.of(ProductId.newId(), 1).hold(0));
    }
}
