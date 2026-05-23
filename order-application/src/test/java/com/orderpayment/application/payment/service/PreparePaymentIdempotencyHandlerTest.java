package com.orderpayment.application.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreparePaymentIdempotencyHandlerTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 5, 21, 10, 0);
    private final PreparePaymentRequestHashService requestHashService = new PreparePaymentRequestHashService();
    private final PreparePaymentIdempotencyResponseSerializer responseSerializer =
            new PreparePaymentIdempotencyResponseSerializer(new ObjectMapper());

    @Mock
    private IdempotencyRecordQueryPort idempotencyRecordQueryPort;

    @Mock
    private IdempotencyRecordCommandPort idempotencyRecordCommandPort;

    @Captor
    private ArgumentCaptor<IdempotencyRecord> recordCaptor;

    private PreparePaymentIdempotencyHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PreparePaymentIdempotencyHandler(
                idempotencyRecordQueryPort,
                idempotencyRecordCommandPort,
                () -> now,
                requestHashService,
                responseSerializer
        );
    }

    @Test
    @DisplayName("resolve 는 멱등키가 처음이면 처리 중 레코드를 생성한다")
    void resolve_whenKeyIsNew_thenCreateInFlightRecord() {
        PreparePaymentCommand command = command(OrderId.newId(), "payment-request-1");
        when(idempotencyRecordQueryPort.findByKey("payment-request-1")).thenReturn(Optional.empty());

        PreparePaymentIdempotencyDecision decision = handler.resolve(command);

        assertThat(decision.hasReplayResult()).isFalse();
        assertThat(decision.inFlightRecord().key()).isEqualTo("payment-request-1");
        assertThat(decision.inFlightRecord().status()).isEqualTo(IdempotencyRecordStatus.IN_FLIGHT);
        assertThat(decision.inFlightRecord().expiresAt()).isEqualTo(now.plusDays(1));
        verify(idempotencyRecordCommandPort).save(decision.inFlightRecord());
    }

    @Test
    @DisplayName("resolve 는 키와 요청 해시가 같으면 완료 응답을 재현한다")
    void resolve_whenCompletedRecordMatches_thenReplayResponse() {
        OrderId orderId = OrderId.newId();
        PaymentId paymentId = PaymentId.newId();
        when(idempotencyRecordQueryPort.findByKey("payment-request-1"))
                .thenReturn(Optional.of(completedRecord("payment-request-1", orderId, paymentId)));

        PreparePaymentIdempotencyDecision decision = handler.resolve(command(orderId, "payment-request-1"));

        assertThat(decision.hasReplayResult()).isTrue();
        assertThat(decision.replayResult().paymentId()).isEqualTo(paymentId);
        assertThat(decision.replayResult().orderId()).isEqualTo(orderId);
        assertThat(decision.replayResult().paymentStatus()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    @DisplayName("resolve 는 같은 키의 요청 해시가 다르면 충돌 예외를 던진다")
    void resolve_whenSameKeyHasDifferentHash_thenThrowConflict() {
        when(idempotencyRecordQueryPort.findByKey("payment-request-1"))
                .thenReturn(Optional.of(completedRecord("payment-request-1", OrderId.newId(), PaymentId.newId())));

        assertThatThrownBy(() -> handler.resolve(command(OrderId.newId(), "payment-request-1")))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    @Test
    @DisplayName("resolve 는 같은 요청 해시가 처리 중이면 예외를 던진다")
    void resolve_whenSameKeyIsInFlight_thenThrowInFlight() {
        OrderId orderId = OrderId.newId();
        PreparePaymentCommand command = command(orderId, "payment-request-1");
        when(idempotencyRecordQueryPort.findByKey("payment-request-1"))
                .thenReturn(Optional.of(IdempotencyRecord.inFlight(
                        "payment-request-1",
                        requestHashService.hash(command),
                        now,
                        now.plusDays(1)
                )));

        assertThatThrownBy(() -> handler.resolve(command))
                .isInstanceOf(IdempotencyInFlightException.class);
    }

    @Test
    @DisplayName("complete 는 직렬화된 응답 본문을 저장한다")
    void complete_whenResultProvided_thenStoreSerializedResponse() {
        PreparePaymentCommand command = command(OrderId.newId(), "payment-request-1");
        IdempotencyRecord inFlightRecord = IdempotencyRecord.inFlight(
                "payment-request-1",
                requestHashService.hash(command),
                now,
                now.plusDays(1)
        );
        PreparePaymentResult result = new PreparePaymentResult(
                PaymentId.newId(),
                command.orderId(),
                Money.won(2000),
                OrderStatus.PAYMENT_PENDING,
                PaymentStatus.READY
        );

        handler.complete(inFlightRecord, result);

        verify(idempotencyRecordCommandPort).save(recordCaptor.capture());
        IdempotencyRecord savedRecord = recordCaptor.getValue();
        assertThat(savedRecord.status()).isEqualTo(IdempotencyRecordStatus.COMPLETED);
        assertThat(responseSerializer.deserialize(savedRecord.responseBody()).toResult()).isEqualTo(result);
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
}
