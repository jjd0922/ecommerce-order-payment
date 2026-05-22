package com.orderpayment.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.order.OrderId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentTest {

    @Test
    @DisplayName("ready creates a READY payment")
    void ready_whenCalled_thenCreateReadyPayment() {
        Payment payment = payment();

        assertThat(payment.status()).isEqualTo(PaymentStatus.READY);
        assertThat(payment.amount()).isEqualTo(Money.won(1000));
    }

    @Test
    @DisplayName("startApproval changes READY payment to PROCESSING")
    void startApproval_whenReady_thenChangeStatusToProcessing() {
        Payment payment = payment();

        payment.startApproval();

        assertThat(payment.status()).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    @DisplayName("approve changes PROCESSING payment to APPROVED")
    void approve_whenProcessing_thenChangeStatusToApproved() {
        Payment payment = payment();

        payment.startApproval();
        payment.approve();

        assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("cancel throws when payment is approved")
    void cancel_whenPaymentApproved_thenThrowException() {
        Payment payment = payment();
        payment.startApproval();
        payment.approve();

        assertThatThrownBy(payment::cancel)
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("approve throws when payment already failed")
    void approve_whenPaymentAlreadyFailed_thenThrowException() {
        Payment payment = payment();
        payment.startApproval();
        payment.fail();

        assertThatThrownBy(payment::approve)
                .isInstanceOf(DomainException.class);
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
