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
class ConfirmPaymentIdempotencyHandlerTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 5, 21, 11, 0);
    private final ConfirmPaymentRequestHashService requestHashService = new ConfirmPaymentRequestHashService();
    private final ConfirmPaymentIdempotencyResponseSerializer responseSerializer =
            new ConfirmPaymentIdempotencyResponseSerializer(new ObjectMapper());

    @Mock
    private IdempotencyRecordQueryPort idempotencyRecordQueryPort;

    @Mock
    private IdempotencyRecordCommandPort idempotencyRecordCommandPort;

    @Captor
    private ArgumentCaptor<IdempotencyRecord> recordCaptor;

    private ConfirmPaymentIdempotencyHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ConfirmPaymentIdempotencyHandler(
                idempotencyRecordQueryPort,
                idempotencyRecordCommandPort,
                () -> now,
                requestHashService,
                responseSerializer
        );
    }

    @Test
    @DisplayName("resolve 는 승인 멱등키가 처음이면 처리 중 레코드를 생성한다")
    void resolve_whenKeyIsNew_thenCreateInFlightRecord() {
        ConfirmPaymentCommand command = command(PaymentId.newId(), "confirm-key-1");
        when(idempotencyRecordQueryPort.findByKey("confirm-key-1")).thenReturn(Optional.empty());

        ConfirmPaymentIdempotencyDecision decision = handler.resolve(command);

        assertThat(decision.hasReplayResult()).isFalse();
        assertThat(decision.inFlightRecord().key()).isEqualTo("confirm-key-1");
        assertThat(decision.inFlightRecord().status()).isEqualTo(IdempotencyRecordStatus.IN_FLIGHT);
        assertThat(decision.inFlightRecord().expiresAt()).isEqualTo(now.plusDays(1));
        verify(idempotencyRecordCommandPort).save(decision.inFlightRecord());
    }

    @Test
    @DisplayName("resolve 는 같은 키와 결제 ID의 완료 응답을 재현한다")
    void resolve_whenCompletedRecordMatches_thenReplayResponse() {
        PaymentId paymentId = PaymentId.newId();
        ConfirmPaymentCommand command = command(paymentId, "confirm-key-1");
        ConfirmPaymentResult completedResult = result(paymentId, PaymentStatus.APPROVED);
        when(idempotencyRecordQueryPort.findByKey("confirm-key-1"))
                .thenReturn(Optional.of(completedRecord(command, completedResult)));

        ConfirmPaymentIdempotencyDecision decision = handler.resolve(command);

        assertThat(decision.hasReplayResult()).isTrue();
        assertThat(decision.replayResult()).isEqualTo(completedResult);
    }

    @Test
    @DisplayName("resolve 는 같은 키로 다른 결제 ID를 요청하면 충돌 예외를 던진다")
    void resolve_whenSameKeyHasDifferentPaymentId_thenThrowConflict() {
        ConfirmPaymentCommand firstCommand = command(PaymentId.newId(), "confirm-key-1");
        when(idempotencyRecordQueryPort.findByKey("confirm-key-1"))
                .thenReturn(Optional.of(completedRecord(
                        firstCommand,
                        result(firstCommand.paymentId(), PaymentStatus.APPROVED)
                )));

        assertThatThrownBy(() -> handler.resolve(command(PaymentId.newId(), "confirm-key-1")))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    @Test
    @DisplayName("resolve 는 처리 중인 승인 요청이면 예외를 던진다")
    void resolve_whenRecordIsInFlight_thenThrowInFlight() {
        ConfirmPaymentCommand command = command(PaymentId.newId(), "confirm-key-1");
        when(idempotencyRecordQueryPort.findByKey("confirm-key-1"))
                .thenReturn(Optional.of(IdempotencyRecord.inFlight(
                        command.idempotencyKey().value(),
                        requestHashService.hash(command),
                        now,
                        now.plusDays(1)
                )));

        assertThatThrownBy(() -> handler.resolve(command))
                .isInstanceOf(IdempotencyInFlightException.class);
    }

    @Test
    @DisplayName("complete 는 결제 승인 응답 본문을 저장한다")
    void complete_whenResultProvided_thenStoreResponseBody() {
        ConfirmPaymentCommand command = command(PaymentId.newId(), "confirm-key-1");
        IdempotencyRecord inFlightRecord = IdempotencyRecord.inFlight(
                command.idempotencyKey().value(),
                requestHashService.hash(command),
                now,
                now.plusDays(1)
        );
        ConfirmPaymentResult result = result(command.paymentId(), PaymentStatus.FAILED);

        handler.complete(inFlightRecord, result);

        verify(idempotencyRecordCommandPort).save(recordCaptor.capture());
        IdempotencyRecord savedRecord = recordCaptor.getValue();
        assertThat(savedRecord.status()).isEqualTo(IdempotencyRecordStatus.COMPLETED);
        assertThat(responseSerializer.deserialize(savedRecord.responseBody()).toResult()).isEqualTo(result);
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
}
