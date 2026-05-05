package com.orderpayment.application.payment.dto;

import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.payment.IdempotencyKey;
import com.orderpayment.domain.payment.PaymentId;
import java.util.Objects;

public record ConfirmPaymentCommand(
        PaymentId paymentId,
        IdempotencyKey idempotencyKey
) {

    public ConfirmPaymentCommand {
        Objects.requireNonNull(paymentId, "payment id must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotency key must not be null");
        if (idempotencyKey.value().isBlank()) {
            throw new DomainException("idempotency key must not be blank");
        }
    }
}
