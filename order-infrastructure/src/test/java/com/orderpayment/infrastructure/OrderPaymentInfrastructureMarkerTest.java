package com.orderpayment.infrastructure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OrderPaymentInfrastructureMarkerTest {

    @Test
    void markerTypeExists() {
        assertTrue(OrderPaymentInfrastructureMarker.class.isInterface());
    }
}
