package com.orderpayment.domain.inventory;

import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.product.ProductId;
import java.util.Objects;

public class Inventory {

    private final ProductId productId;
    private int quantity;

    private Inventory(ProductId productId, int quantity) {
        this.productId = Objects.requireNonNull(productId, "product id must not be null");
        validateQuantity(quantity);
        this.quantity = quantity;
    }

    public static Inventory of(ProductId productId, int quantity) {
        return new Inventory(productId, quantity);
    }

    public ProductId productId() {
        return productId;
    }

    public int quantity() {
        return quantity;
    }

    public void deduct(int orderQuantity) {
        validateOrderQuantity(orderQuantity);
        if (quantity < orderQuantity) {
            throw new DomainException("inventory is insufficient");
        }
        quantity -= orderQuantity;
    }

    public void increase(int quantity) {
        validateOrderQuantity(quantity);
        this.quantity += quantity;
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
