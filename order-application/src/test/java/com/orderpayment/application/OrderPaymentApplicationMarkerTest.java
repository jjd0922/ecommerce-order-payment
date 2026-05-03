package com.orderpayment.application;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OrderPaymentApplicationMarkerTest {

    @Test
    void markerTypeExists() {
        assertTrue(OrderPaymentApplicationMarker.class.isInterface());
    }
}
