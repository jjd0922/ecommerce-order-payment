package com.orderpayment.application.payment.port.out;

import com.orderpayment.domain.payment.IdempotencyKey;
import com.orderpayment.domain.payment.Payment;
import com.orderpayment.domain.payment.PaymentId;
import java.util.Optional;

public interface PaymentQueryPort {

    Payment getPayment(PaymentId paymentId);

    Payment getPaymentForUpdate(PaymentId paymentId);

    Optional<Payment> findByIdempotencyKey(IdempotencyKey idempotencyKey);
}
