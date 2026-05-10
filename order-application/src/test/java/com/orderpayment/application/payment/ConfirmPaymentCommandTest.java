package com.orderpayment.application.payment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderpayment.application.payment.dto.ConfirmPaymentCommand;
import com.orderpayment.domain.payment.IdempotencyKey;
import com.orderpayment.domain.payment.PaymentId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConfirmPaymentCommandTest {

    @Test
    @DisplayName("ConfirmPaymentCommand 는 paymentId 가 null 이면 예외를 던진다")
    void constructor_whenPaymentIdNull_thenThrowException() {
        assertThatThrownBy(() -> new ConfirmPaymentCommand(null, new IdempotencyKey("payment-request-1")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("ConfirmPaymentCommand 는 idempotencyKey 가 null 이면 예외를 던진다")
    void constructor_whenIdempotencyKeyNull_thenThrowException() {
        assertThatThrownBy(() -> new ConfirmPaymentCommand(PaymentId.newId(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
