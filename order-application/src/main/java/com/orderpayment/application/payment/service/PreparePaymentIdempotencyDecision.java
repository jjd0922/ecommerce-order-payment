package com.orderpayment.application.payment.service;

import com.orderpayment.application.payment.dto.PreparePaymentResult;
import com.orderpayment.domain.idempotency.IdempotencyRecord;

record PreparePaymentIdempotencyDecision(
        PreparePaymentResult replayResult,
        IdempotencyRecord inFlightRecord
) {

    static PreparePaymentIdempotencyDecision replay(PreparePaymentResult result) {
        return new PreparePaymentIdempotencyDecision(result, null);
    }

    static PreparePaymentIdempotencyDecision proceed(IdempotencyRecord record) {
        return new PreparePaymentIdempotencyDecision(null, record);
    }

    boolean hasReplayResult() {
        return replayResult != null;
    }
}
