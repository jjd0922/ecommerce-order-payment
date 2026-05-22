package com.orderpayment.application.idempotency.port.out;

import com.orderpayment.domain.idempotency.IdempotencyRecord;

public interface IdempotencyRecordCommandPort {

    void save(IdempotencyRecord record);
}
