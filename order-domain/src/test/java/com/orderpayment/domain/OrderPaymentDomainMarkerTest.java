package com.orderpayment.domain;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OrderPaymentDomainMarkerTest {

    @Test
    void markerTypeExists() {
        assertTrue(OrderPaymentDomainMarker.class.isInterface());
    }
}
