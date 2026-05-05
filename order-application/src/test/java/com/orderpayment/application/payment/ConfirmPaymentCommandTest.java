package com.orderpayment.application.payment;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orderpayment.application.payment.dto.ConfirmPaymentCommand;
import com.orderpayment.domain.payment.IdempotencyKey;
import com.orderpayment.domain.payment.PaymentId;
import org.junit.jupiter.api.Test;

class ConfirmPaymentCommandTest {

    @Test
    void rejectsNullPaymentId() {
        assertThrows(NullPointerException.class, () -> new ConfirmPaymentCommand(
                null,
                new IdempotencyKey("payment-request-1")
        ));
    }

    @Test
    void rejectsNullIdempotencyKey() {
        assertThrows(NullPointerException.class, () -> new ConfirmPaymentCommand(
                PaymentId.newId(),
                null
        ));
    }
}
