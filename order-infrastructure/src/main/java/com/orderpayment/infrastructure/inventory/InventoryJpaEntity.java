package com.orderpayment.infrastructure.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory")
public class InventoryJpaEntity {

    @Id
    @Column(name = "product_id", nullable = false, length = 36)
    private String productId;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(name = "held_quantity", nullable = false)
    private int heldQuantity;

    protected InventoryJpaEntity() {
    }

    public InventoryJpaEntity(String productId, int availableQuantity, int heldQuantity) {
        this.productId = productId;
        this.availableQuantity = availableQuantity;
        this.heldQuantity = heldQuantity;
    }

    public String productId() {
        return productId;
    }

    public int availableQuantity() {
        return availableQuantity;
    }

    public int heldQuantity() {
        return heldQuantity;
    }
}
