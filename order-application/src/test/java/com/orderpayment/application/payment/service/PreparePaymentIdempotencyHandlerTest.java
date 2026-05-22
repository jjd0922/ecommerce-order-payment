package com.orderpayment.application.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderpayment.application.idempotency.IdempotencyInFlightException;
import com.orderpayment.application.idempotency.port.out.IdempotencyRecordCommandPort;
import com.orderpayment.application.idempotency.port.out.IdempotencyRecordQueryPort;
import com.orderpayment.application.payment.IdempotencyKeyConflictException;
import com.orderpayment.application.payment.dto.PreparePaymentCommand;
import com.orderpayment.application.payment.dto.PreparePaymentResult;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PreparePaymentIdempotencyHandlerTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 5, 21, 10, 0);
    private final FakeIdempotencyRecordPort idempotencyRecordPort = new FakeIdempotencyRecordPort();
    private final PreparePaymentRequestHashService requestHashService = new PreparePaymentRequestHashService();
    private final PreparePaymentIdempotencyResponseSerializer responseSerializer =
            new PreparePaymentIdempotencyResponseSerializer(new ObjectMapper());
    private final PreparePaymentIdempotencyHandler handler = new PreparePaymentIdempotencyHandler(
            idempotencyRecordPort,
            idempotencyRecordPort,
            () -> now,
            requestHashService,
            responseSerializer
    );

    @Test
    @DisplayName("resolve creates in-flight record when key is new")
    void resolve_whenKeyIsNew_thenCreateInFlightRecord() {
        PreparePaymentCommand command = command(OrderId.newId(), "payment-request-1");

        PreparePaymentIdempotencyDecision decision = handler.resolve(command);

        assertThat(decision.hasReplayResult()).isFalse();
        assertThat(decision.inFlightRecord().key()).isEqualTo("payment-request-1");
        assertThat(decision.inFlightRecord().status()).isEqualTo(IdempotencyRecordStatus.IN_FLIGHT);
        assertThat(decision.inFlightRecord().expiresAt()).isEqualTo(now.plusDays(1));
        assertThat(idempotencyRecordPort.findByKey("payment-request-1")).isPresent();
    }

    @Test
    @DisplayName("resolve replays completed response when key and request hash match")
    void resolve_whenCompletedRecordMatches_thenReplayResponse() {
        OrderId orderId = OrderId.newId();
        PaymentId paymentId = PaymentId.newId();
        idempotencyRecordPort.save(completedRecord("payment-request-1", orderId, paymentId));

        PreparePaymentIdempotencyDecision decision = handler.resolve(command(orderId, "payment-request-1"));

        assertThat(decision.hasReplayResult()).isTrue();
        assertThat(decision.replayResult().paymentId()).isEqualTo(paymentId);
        assertThat(decision.replayResult().orderId()).isEqualTo(orderId);
        assertThat(decision.replayResult().paymentStatus()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    @DisplayName("resolve rejects same key with different request hash")
    void resolve_whenSameKeyHasDifferentHash_thenThrowConflict() {
        idempotencyRecordPort.save(completedRecord("payment-request-1", OrderId.newId(), PaymentId.newId()));

        assertThatThrownBy(() -> handler.resolve(command(OrderId.newId(), "payment-request-1")))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    @Test
    @DisplayName("resolve rejects in-flight record with same request hash")
    void resolve_whenSameKeyIsInFlight_thenThrowInFlight() {
        OrderId orderId = OrderId.newId();
        PreparePaymentCommand command = command(orderId, "payment-request-1");
        idempotencyRecordPort.save(IdempotencyRecord.inFlight(
                "payment-request-1",
                requestHashService.hash(command),
                now,
                now.plusDays(1)
        ));

        assertThatThrownBy(() -> handler.resolve(command))
                .isInstanceOf(IdempotencyInFlightException.class);
    }

    @Test
    @DisplayName("complete stores serialized response body")
    void complete_whenResultProvided_thenStoreSerializedResponse() {
        PreparePaymentCommand command = command(OrderId.newId(), "payment-request-1");
        PreparePaymentIdempotencyDecision decision = handler.resolve(command);
        PreparePaymentResult result = new PreparePaymentResult(
                PaymentId.newId(),
                command.orderId(),
                Money.won(2000),
                OrderStatus.PAYMENT_PENDING,
                PaymentStatus.READY
        );

        handler.complete(decision.inFlightRecord(), result);

        IdempotencyRecord record = idempotencyRecordPort.findByKey("payment-request-1").orElseThrow();
        assertThat(record.status()).isEqualTo(IdempotencyRecordStatus.COMPLETED);
        assertThat(responseSerializer.deserialize(record.responseBody()).toResult()).isEqualTo(result);
    }

    private IdempotencyRecord completedRecord(String key, OrderId orderId, PaymentId paymentId) {
        PreparePaymentCommand command = command(orderId, key);
        PreparePaymentResult result = new PreparePaymentResult(
                paymentId,
                orderId,
                Money.won(2000),
                OrderStatus.PAYMENT_PENDING,
                PaymentStatus.READY
        );
        IdempotencyRecord record = IdempotencyRecord.inFlight(
                key,
                requestHashService.hash(command),
                now,
                now.plusDays(1)
        );
        record.complete(responseSerializer.serialize(PreparePaymentIdempotencyResponse.from(result)));
        return record;
    }

    private static PreparePaymentCommand command(OrderId orderId, String idempotencyKey) {
        return new PreparePaymentCommand(orderId, new IdempotencyKey(idempotencyKey));
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
