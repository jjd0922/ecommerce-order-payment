package com.orderpayment.application.order.port.out;

import com.orderpayment.domain.inventory.Inventory;

public interface InventoryCommandPort {

    void saveInventory(Inventory inventory);
}
