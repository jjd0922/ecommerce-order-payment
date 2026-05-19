package com.orderpayment.application.payment.service;

import com.orderpayment.application.payment.dto.ConfirmPaymentResult;
import com.orderpayment.domain.payment.Payment;

record ConfirmPaymentAttempt(
        Payment payment,
        ConfirmPaymentResult completedResult
) {

    static ConfirmPaymentAttempt readyForApproval(Payment payment) {
        return new ConfirmPaymentAttempt(payment, null);
    }

    static ConfirmPaymentAttempt completed(ConfirmPaymentResult result) {
        return new ConfirmPaymentAttempt(null, result);
    }

    boolean requiresApproval() {
        return payment != null;
    }
}
