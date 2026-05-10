package com.orderpayment.application.payment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderpayment.application.payment.dto.PreparePaymentCommand;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.payment.IdempotencyKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PreparePaymentCommandTest {

    @Test
    @DisplayName("PreparePaymentCommand 는 필수 값이 null 이면 예외를 던진다")
    void constructor_whenRequiredValueNull_thenThrowException() {
        assertThatThrownBy(() -> new PreparePaymentCommand(null, new IdempotencyKey("payment-request-1")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PreparePaymentCommand(OrderId.newId(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
