package com.orderpayment.domain.product;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.common.Money;
import org.junit.jupiter.api.Test;

class ProductTest {

    @Test
    void rejectsBlankName() {
        assertThrows(DomainException.class, () -> Product.selling(ProductId.newId(), " ", Money.won(1000)));
    }

    @Test
    void stopsSelling() {
        Product product = Product.selling(ProductId.newId(), "keyboard", Money.won(1000));

        product.stopSelling();

        assertFalse(product.isSelling());
        assertThrows(DomainException.class, product::ensureSelling);
    }
}
