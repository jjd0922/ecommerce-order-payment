package com.orderpayment.api;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderPaymentApplicationTest {

    @Test
    @DisplayName("OrderPaymentApplication 클래스는 기본 생성자를 조회할 수 있다")
    void applicationClass_whenLoaded_thenConstructorIsAvailable() {
        assertThatCode(() -> OrderPaymentApplication.class.getDeclaredConstructor())
                .doesNotThrowAnyException();
    }
}
