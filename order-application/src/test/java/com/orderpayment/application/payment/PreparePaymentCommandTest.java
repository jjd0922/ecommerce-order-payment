package com.orderpayment.application.payment;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orderpayment.application.payment.dto.PreparePaymentCommand;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.payment.IdempotencyKey;
import org.junit.jupiter.api.Test;

class PreparePaymentCommandTest {

    @Test
    void rejectsNullValue() {
        assertThrows(NullPointerException.class, () -> new PreparePaymentCommand(
                null,
                new IdempotencyKey("payment-request-1")
        ));
        assertThrows(NullPointerException.class, () -> new PreparePaymentCommand(
                OrderId.newId(),
                null
        ));
    }
}
