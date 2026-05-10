package com.orderpayment.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderPaymentApplicationMarkerTest {

    @Test
    @DisplayName("OrderPaymentApplicationMarker 는 인터페이스이다")
    void markerType_whenLoaded_thenIsInterface() {
        assertThat(OrderPaymentApplicationMarker.class.isInterface()).isTrue();
    }
}
