package com.orderpayment.domain.product;

import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.common.Money;
import java.util.Objects;

public class Product {

    private final ProductId id;
    private final String name;
    private final Money price;
    private boolean selling;

    private Product(ProductId id, String name, Money price, boolean selling) {
        this.id = Objects.requireNonNull(id, "product id must not be null");
        this.name = validateName(name);
        this.price = Objects.requireNonNull(price, "product price must not be null");
        this.selling = selling;
    }

    public static Product selling(ProductId id, String name, Money price) {
        return new Product(id, name, price, true);
    }

    public ProductId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Money price() {
        return price;
    }

    public boolean isSelling() {
        return selling;
    }

    public void stopSelling() {
        selling = false;
    }

    public void ensureSelling() {
        if (!selling) {
            throw new DomainException("product is not selling");
        }
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainException("product name must not be blank");
        }
        return name;
    }
}
