package com.orderpayment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderPaymentDomainMarkerTest {

    @Test
    @DisplayName("OrderPaymentDomainMarker 는 인터페이스이다")
    void markerType_whenLoaded_thenIsInterface() {
        assertThat(OrderPaymentDomainMarker.class.isInterface()).isTrue();
    }
}
