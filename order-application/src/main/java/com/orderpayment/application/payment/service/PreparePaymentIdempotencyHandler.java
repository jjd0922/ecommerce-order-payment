package com.orderpayment.application.payment.service;

import com.orderpayment.application.common.port.out.CurrentTimePort;
import com.orderpayment.application.idempotency.IdempotencyInFlightException;
import com.orderpayment.application.idempotency.port.out.IdempotencyRecordCommandPort;
import com.orderpayment.application.idempotency.port.out.IdempotencyRecordQueryPort;
import com.orderpayment.application.payment.IdempotencyKeyConflictException;
import com.orderpayment.application.payment.dto.PreparePaymentCommand;
import com.orderpayment.application.payment.dto.PreparePaymentResult;
import com.orderpayment.domain.idempotency.IdempotencyRecord;
import com.orderpayment.domain.idempotency.IdempotencyRecordStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PreparePaymentIdempotencyHandler {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(1);

    private final IdempotencyRecordQueryPort idempotencyRecordQueryPort;
    private final IdempotencyRecordCommandPort idempotencyRecordCommandPort;
    private final CurrentTimePort currentTimePort;
    private final PreparePaymentRequestHashService requestHashService;
    private final PreparePaymentIdempotencyResponseSerializer responseSerializer;

    PreparePaymentIdempotencyDecision resolve(PreparePaymentCommand command) {
        String requestHash = requestHashService.hash(command);
        return idempotencyRecordQueryPort.findByKey(command.idempotencyKey().value())
                .map(record -> PreparePaymentIdempotencyDecision.replay(replay(record, requestHash)))
                .orElseGet(() -> PreparePaymentIdempotencyDecision.proceed(createInFlightRecord(command, requestHash)));
    }

    PreparePaymentResult replayExisting(PreparePaymentCommand command) {
        String requestHash = requestHashService.hash(command);
        IdempotencyRecord record = idempotencyRecordQueryPort.findByKey(command.idempotencyKey().value())
                .orElseThrow(() -> new IdempotencyInFlightException("idempotency request is in flight"));
        return replay(record, requestHash);
    }

    void complete(IdempotencyRecord record, PreparePaymentResult result) {
        record.complete(responseSerializer.serialize(PreparePaymentIdempotencyResponse.from(result)));
        idempotencyRecordCommandPort.save(record);
    }

    private PreparePaymentResult replay(IdempotencyRecord record, String requestHash) {
        if (!record.hasSameRequestHash(requestHash)) {
            throw new IdempotencyKeyConflictException("idempotency key was already used with different payment request");
        }
        if (record.status() == IdempotencyRecordStatus.IN_FLIGHT) {
            throw new IdempotencyInFlightException("idempotency request is in flight");
        }
        return responseSerializer.deserialize(record.responseBody()).toResult();
    }

    private IdempotencyRecord createInFlightRecord(PreparePaymentCommand command, String requestHash) {
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
