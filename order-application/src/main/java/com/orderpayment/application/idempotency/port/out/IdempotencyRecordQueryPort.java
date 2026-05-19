package com.orderpayment.application.idempotency.port.out;

import com.orderpayment.domain.idempotency.IdempotencyRecord;
import java.util.Optional;

public interface IdempotencyRecordQueryPort {

    Optional<IdempotencyRecord> findByKey(String key);
}
