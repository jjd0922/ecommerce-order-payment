package com.orderpayment.application.payment.port.out;

import com.orderpayment.domain.payment.IdempotencyKey;
import com.orderpayment.domain.payment.Payment;
import java.util.Optional;

public interface PaymentQueryPort {

    Optional<Payment> findByIdempotencyKey(IdempotencyKey idempotencyKey);
}
