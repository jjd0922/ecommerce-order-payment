package com.orderpayment.application.payment.service;

import com.orderpayment.application.common.port.out.CurrentTimePort;
import com.orderpayment.application.idempotency.IdempotencyInFlightException;
import com.orderpayment.application.idempotency.port.out.IdempotencyRecordCommandPort;
import com.orderpayment.application.idempotency.port.out.IdempotencyRecordQueryPort;
import com.orderpayment.application.payment.IdempotencyKeyConflictException;
import com.orderpayment.application.payment.dto.ConfirmPaymentCommand;
import com.orderpayment.application.payment.dto.ConfirmPaymentResult;
import com.orderpayment.domain.idempotency.IdempotencyRecord;
import com.orderpayment.domain.idempotency.IdempotencyRecordStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConfirmPaymentIdempotencyHandler {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(1);

    private final IdempotencyRecordQueryPort idempotencyRecordQueryPort;
    private final IdempotencyRecordCommandPort idempotencyRecordCommandPort;
    private final CurrentTimePort currentTimePort;
    private final ConfirmPaymentRequestHashService requestHashService;
    private final ConfirmPaymentIdempotencyResponseSerializer responseSerializer;

    ConfirmPaymentIdempotencyDecision resolve(ConfirmPaymentCommand command) {
        String requestHash = requestHashService.hash(command);
        return idempotencyRecordQueryPort.findByKey(command.idempotencyKey().value())
                .map(record -> ConfirmPaymentIdempotencyDecision.replay(replay(record, requestHash)))
                .orElseGet(() -> ConfirmPaymentIdempotencyDecision.proceed(createInFlightRecord(command, requestHash)));
    }

    void complete(IdempotencyRecord record, ConfirmPaymentResult result) {
        record.complete(responseSerializer.serialize(ConfirmPaymentIdempotencyResponse.from(result)));
        idempotencyRecordCommandPort.save(record);
    }

    private ConfirmPaymentResult replay(IdempotencyRecord record, String requestHash) {
        if (!record.hasSameRequestHash(requestHash)) {
            throw new IdempotencyKeyConflictException("idempotency key was already used with different confirm request");
        }
        if (record.status() == IdempotencyRecordStatus.IN_FLIGHT) {
            throw new IdempotencyInFlightException("idempotency request is in flight");
        }
        return responseSerializer.deserialize(record.responseBody()).toResult();
    }

    private IdempotencyRecord createInFlightRecord(ConfirmPaymentCommand command, String requestHash) {
        LocalDateTime now = currentTimePort.now();
        IdempotencyRecord record = IdempotencyRecord.inFlight(
                command.idempotencyKey().value(),
                requestHash,
                now,
                now.plus(IDEMPOTENCY_TTL)
        );
        idempotencyRecordCommandPort.save(record);
        return record;
    }
}
