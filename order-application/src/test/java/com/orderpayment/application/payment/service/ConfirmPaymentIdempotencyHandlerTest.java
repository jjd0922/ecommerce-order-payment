package com.orderpayment.application.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderpayment.application.idempotency.IdempotencyInFlightException;
import com.orderpayment.application.idempotency.port.out.IdempotencyRecordCommandPort;
import com.orderpayment.application.idempotency.port.out.IdempotencyRecordQueryPort;
import com.orderpayment.application.payment.IdempotencyKeyConflictException;
import com.orderpayment.application.payment.dto.ConfirmPaymentCommand;
import com.orderpayment.application.payment.dto.ConfirmPaymentResult;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.idempotency.IdempotencyRecord;
import com.orderpayment.domain.idempotency.IdempotencyRecordStatus;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.order.OrderStatus;
import com.orderpayment.domain.payment.IdempotencyKey;
import com.orderpayment.domain.payment.PaymentId;
import com.orderpayment.domain.payment.PaymentStatus;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConfirmPaymentIdempotencyHandlerTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 5, 21, 11, 0);
    private final FakeIdempotencyRecordPort idempotencyRecordPort = new FakeIdempotencyRecordPort();
    private final ConfirmPaymentRequestHashService requestHashService = new ConfirmPaymentRequestHashService();
    private final ConfirmPaymentIdempotencyResponseSerializer responseSerializer =
            new ConfirmPaymentIdempotencyResponseSerializer(new ObjectMapper());
    private final ConfirmPaymentIdempotencyHandler handler = new ConfirmPaymentIdempotencyHandler(
            idempotencyRecordPort,
            idempotencyRecordPort,
            () -> now,
            requestHashService,
            responseSerializer
    );

    @Test
    @DisplayName("resolve creates in-flight record when confirm key is new")
    void resolve_whenKeyIsNew_thenCreateInFlightRecord() {
        ConfirmPaymentCommand command = command(PaymentId.newId(), "confirm-key-1");

        ConfirmPaymentIdempotencyDecision decision = handler.resolve(command);

        assertThat(decision.hasReplayResult()).isFalse();
        assertThat(decision.inFlightRecord().key()).isEqualTo("confirm-key-1");
        assertThat(decision.inFlightRecord().status()).isEqualTo(IdempotencyRecordStatus.IN_FLIGHT);
        assertThat(decision.inFlightRecord().expiresAt()).isEqualTo(now.plusDays(1));
    }

    @Test
    @DisplayName("resolve replays completed response for same key and payment id")
    void resolve_whenCompletedRecordMatches_thenReplayResponse() {
        PaymentId paymentId = PaymentId.newId();
        ConfirmPaymentCommand command = command(paymentId, "confirm-key-1");
        ConfirmPaymentResult completedResult = result(paymentId, PaymentStatus.APPROVED);
        idempotencyRecordPort.save(completedRecord(command, completedResult));

        ConfirmPaymentIdempotencyDecision decision = handler.resolve(command);

        assertThat(decision.hasReplayResult()).isTrue();
        assertThat(decision.replayResult()).isEqualTo(completedResult);
    }

    @Test
    @DisplayName("resolve rejects same key with different payment id")
    void resolve_whenSameKeyHasDifferentPaymentId_thenThrowConflict() {
        ConfirmPaymentCommand firstCommand = command(PaymentId.newId(), "confirm-key-1");
        idempotencyRecordPort.save(completedRecord(firstCommand, result(firstCommand.paymentId(), PaymentStatus.APPROVED)));

        assertThatThrownBy(() -> handler.resolve(command(PaymentId.newId(), "confirm-key-1")))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    @Test
    @DisplayName("resolve rejects in-flight confirm request")
    void resolve_whenRecordIsInFlight_thenThrowInFlight() {
        ConfirmPaymentCommand command = command(PaymentId.newId(), "confirm-key-1");
        idempotencyRecordPort.save(IdempotencyRecord.inFlight(
                command.idempotencyKey().value(),
                requestHashService.hash(command),
                now,
                now.plusDays(1)
        ));

        assertThatThrownBy(() -> handler.resolve(command))
                .isInstanceOf(IdempotencyInFlightException.class);
    }

    @Test
    @DisplayName("complete stores confirm response body")
    void complete_whenResultProvided_thenStoreResponseBody() {
        ConfirmPaymentCommand command = command(PaymentId.newId(), "confirm-key-1");
        ConfirmPaymentIdempotencyDecision decision = handler.resolve(command);
        ConfirmPaymentResult result = result(command.paymentId(), PaymentStatus.FAILED);

        handler.complete(decision.inFlightRecord(), result);

        IdempotencyRecord record = idempotencyRecordPort.findByKey("confirm-key-1").orElseThrow();
        assertThat(record.status()).isEqualTo(IdempotencyRecordStatus.COMPLETED);
        assertThat(responseSerializer.deserialize(record.responseBody()).toResult()).isEqualTo(result);
    }

    private IdempotencyRecord completedRecord(ConfirmPaymentCommand command, ConfirmPaymentResult result) {
        IdempotencyRecord record = IdempotencyRecord.inFlight(
                command.idempotencyKey().value(),
                requestHashService.hash(command),
                now,
                now.plusDays(1)
        );
        record.complete(responseSerializer.serialize(ConfirmPaymentIdempotencyResponse.from(result)));
        return record;
    }

    private static ConfirmPaymentCommand command(PaymentId paymentId, String idempotencyKey) {
        return new ConfirmPaymentCommand(paymentId, new IdempotencyKey(idempotencyKey));
    }

    private static ConfirmPaymentResult result(PaymentId paymentId, PaymentStatus paymentStatus) {
        OrderStatus orderStatus = paymentStatus == PaymentStatus.APPROVED ? OrderStatus.PAID : OrderStatus.FAILED;
        return new ConfirmPaymentResult(
                paymentId,
                OrderId.newId(),
                Money.won(2000),
                orderStatus,
                paymentStatus
        );
    }

    private static class FakeIdempotencyRecordPort
            implements IdempotencyRecordQueryPort, IdempotencyRecordCommandPort {

        private final Map<String, IdempotencyRecord> records = new ConcurrentHashMap<>();

        @Override
        public Optional<IdempotencyRecord> findByKey(String key) {
            return Optional.ofNullable(records.get(key));
        }

        @Override
        public void save(IdempotencyRecord record) {
            records.put(record.key(), record);
        }
    }
}
