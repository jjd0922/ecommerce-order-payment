package com.orderpayment.application.order.port.out;

import com.orderpayment.domain.inventory.Inventory;
import com.orderpayment.domain.product.ProductId;

public interface InventoryQueryPort {

    Inventory getInventory(ProductId productId);
}
