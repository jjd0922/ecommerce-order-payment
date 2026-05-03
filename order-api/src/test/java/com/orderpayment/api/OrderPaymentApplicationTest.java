package com.orderpayment.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class OrderPaymentApplicationTest {

    @Test
    void applicationClassExists() {
        assertDoesNotThrow(() -> OrderPaymentApplication.class.getDeclaredConstructor());
    }
}
