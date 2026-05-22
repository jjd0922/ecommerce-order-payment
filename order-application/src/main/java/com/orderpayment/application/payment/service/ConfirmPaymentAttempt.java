package com.orderpayment.application.payment.service;

import com.orderpayment.application.payment.dto.ConfirmPaymentResult;
import com.orderpayment.domain.idempotency.IdempotencyRecord;
import com.orderpayment.domain.payment.Payment;

record ConfirmPaymentAttempt(
        Payment payment,
        ConfirmPaymentResult completedResult,
        IdempotencyRecord inFlightRecord
) {

    static ConfirmPaymentAttempt readyForApproval(Payment payment, IdempotencyRecord record) {
        return new ConfirmPaymentAttempt(payment, null, record);
    }

    static ConfirmPaymentAttempt completed(ConfirmPaymentResult result) {
        return new ConfirmPaymentAttempt(null, result, null);
    }

    static ConfirmPaymentAttempt completed(ConfirmPaymentResult result, IdempotencyRecord record) {
        return new ConfirmPaymentAttempt(null, result, record);
    }

    boolean requiresApproval() {
        return payment != null;
    }
}
