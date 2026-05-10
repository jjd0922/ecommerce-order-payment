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
    @DisplayName("ready 는 READY 상태의 결제를 생성한다")
    void ready_whenCalled_thenCreateReadyPayment() {
        Payment payment = payment();

        assertThat(payment.status()).isEqualTo(PaymentStatus.READY);
        assertThat(payment.amount()).isEqualTo(Money.won(1000));
    }

    @Test
    @DisplayName("approve 는 결제 상태를 APPROVED 로 변경한다")
    void approve_whenReady_thenChangeStatusToApproved() {
        Payment payment = payment();

        payment.approve();

        assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("cancel 은 승인된 결제이면 예외를 던진다")
    void cancel_whenPaymentApproved_thenThrowException() {
        Payment payment = payment();
        payment.approve();

        assertThatThrownBy(payment::cancel)
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("approve 는 이미 실패한 결제이면 예외를 던진다")
    void approve_whenPaymentAlreadyFailed_thenThrowException() {
        Payment payment = payment();
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
