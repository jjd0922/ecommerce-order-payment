package com.orderpayment.domain.inventory;

import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.product.ProductId;
import java.util.Objects;

public class Inventory {

    private final ProductId productId;
    private int availableQuantity;
    private int heldQuantity;

    private Inventory(ProductId productId, int availableQuantity, int heldQuantity) {
        this.productId = Objects.requireNonNull(productId, "product id must not be null");
        validateQuantity(availableQuantity);
        validateQuantity(heldQuantity);
        this.availableQuantity = availableQuantity;
        this.heldQuantity = heldQuantity;
    }

    public static Inventory of(ProductId productId, int availableQuantity) {
        return new Inventory(productId, availableQuantity, 0);
    }

    public static Inventory restore(ProductId productId, int availableQuantity, int heldQuantity) {
        return new Inventory(productId, availableQuantity, heldQuantity);
    }

    public ProductId productId() {
        return productId;
    }

    public int quantity() {
        return availableQuantity;
    }

    public int availableQuantity() {
        return availableQuantity;
    }

    public int heldQuantity() {
        return heldQuantity;
    }

    public void hold(int quantity) {
        validateOrderQuantity(quantity);
        if (availableQuantity < quantity) {
            throw new DomainException("inventory is insufficient");
        }
        availableQuantity -= quantity;
        heldQuantity += quantity;
    }

    public void release(int quantity) {
        validateOrderQuantity(quantity);
        if (heldQuantity < quantity) {
            throw new DomainException("held inventory is insufficient");
        }
        heldQuantity -= quantity;
        availableQuantity += quantity;
    }

    public void confirm(int quantity) {
        validateOrderQuantity(quantity);
        if (heldQuantity < quantity) {
            throw new DomainException("held inventory is insufficient");
        }
        heldQuantity -= quantity;
    }

    public void deduct(int orderQuantity) {
        hold(orderQuantity);
        confirm(orderQuantity);
    }

    public void increase(int quantity) {
        validateOrderQuantity(quantity);
        this.availableQuantity += quantity;
    }

    private static void validateQuantity(int quantity) {
        if (quantity < 0) {
            throw new DomainException("inventory quantity must not be negative");
        }
    }

    private static void validateOrderQuantity(int quantity) {
        if (quantity <= 0) {
            throw new DomainException("quantity must be positive");
        }
    }
}
