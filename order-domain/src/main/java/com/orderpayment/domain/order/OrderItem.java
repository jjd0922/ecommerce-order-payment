package com.orderpayment.domain.order;

import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.product.ProductId;
import java.util.Objects;

public class OrderItem {

    private final ProductId productId;
    private final String productName;
    private final Money unitPrice;
    private final int quantity;

    private OrderItem(ProductId productId, String productName, Money unitPrice, int quantity) {
        this.productId = Objects.requireNonNull(productId, "product id must not be null");
        this.productName = validateProductName(productName);
        this.unitPrice = Objects.requireNonNull(unitPrice, "unit price must not be null");
        this.quantity = validateQuantity(quantity);
    }

    public static OrderItem of(ProductId productId, String productName, Money unitPrice, int quantity) {
        return new OrderItem(productId, productName, unitPrice, quantity);
    }

    public ProductId productId() {
        return productId;
    }

    public String productName() {
        return productName;
    }

    public Money unitPrice() {
        return unitPrice;
    }

    public int quantity() {
        return quantity;
    }

    public Money subtotal() {
        return unitPrice.multiply(quantity);
    }

    private static String validateProductName(String productName) {
        if (productName == null || productName.isBlank()) {
            throw new DomainException("order item product name must not be blank");
        }
        return productName;
    }

    private static int validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new DomainException("order item quantity must be positive");
        }
        return quantity;
    }
}
