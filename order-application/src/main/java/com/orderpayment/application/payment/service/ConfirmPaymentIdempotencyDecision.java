package com.orderpayment.application.payment.service;

import com.orderpayment.application.payment.dto.ConfirmPaymentResult;
import com.orderpayment.domain.idempotency.IdempotencyRecord;

record ConfirmPaymentIdempotencyDecision(
        ConfirmPaymentResult replayResult,
        IdempotencyRecord inFlightRecord
) {

    static ConfirmPaymentIdempotencyDecision replay(ConfirmPaymentResult result) {
        return new ConfirmPaymentIdempotencyDecision(result, null);
    }

    static ConfirmPaymentIdempotencyDecision proceed(IdempotencyRecord record) {
        return new ConfirmPaymentIdempotencyDecision(null, record);
    }

    boolean hasReplayResult() {
        return replayResult != null;
    }
}
