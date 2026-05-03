package com.orderpayment.domain.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void createsMoneyWithScale() {
        Money money = Money.won(1000);

        assertEquals(new BigDecimal("1000.00"), money.amount());
    }

    @Test
    void rejectsNegativeAmount() {
        assertThrows(DomainException.class, () -> Money.won(-1));
    }

    @Test
    void calculatesAdditionAndMultiplication() {
        Money result = Money.won(1000).add(Money.won(500)).multiply(2);

        assertEquals(Money.won(3000), result);
    }
}
