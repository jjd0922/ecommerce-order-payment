package com.orderpayment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderPaymentInfrastructureMarkerTest {

    @Test
    @DisplayName("OrderPaymentInfrastructureMarker 는 인터페이스이다")
    void markerType_whenLoaded_thenIsInterface() {
        assertThat(OrderPaymentInfrastructureMarker.class.isInterface()).isTrue();
    }
}
