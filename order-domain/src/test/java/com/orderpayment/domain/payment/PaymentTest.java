package com.orderpayment.domain.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.order.OrderId;
import org.junit.jupiter.api.Test;

class PaymentTest {

    @Test
    void createsReadyPayment() {
        Payment payment = payment();

        assertEquals(PaymentStatus.READY, payment.status());
        assertEquals(Money.won(1000), payment.amount());
    }

    @Test
    void approvesPayment() {
        Payment payment = payment();

        payment.approve();

        assertEquals(PaymentStatus.APPROVED, payment.status());
    }

    @Test
    void rejectsApprovedPaymentCancellation() {
        Payment payment = payment();
        payment.approve();

        assertThrows(DomainException.class, payment::cancel);
    }

    @Test
    void rejectsDuplicatedTerminalTransition() {
        Payment payment = payment();
        payment.fail();

        assertThrows(DomainException.class, payment::approve);
    }

    private static Payment payment() {
        return Payment.ready(
                PaymentId.newId(),
                OrderId.newId(),
                Money.won(1000),
                new IdempotencyKey("payment-request-1")
        );
    }
}
