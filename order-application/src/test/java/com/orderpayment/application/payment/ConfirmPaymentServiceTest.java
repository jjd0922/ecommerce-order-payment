package com.orderpayment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderpayment.application.common.port.out.DomainEventPublisherPort;
import com.orderpayment.application.idempotency.port.out.IdempotencyRecordCommandPort;
import com.orderpayment.application.idempotency.port.out.IdempotencyRecordQueryPort;
import com.orderpayment.application.order.port.out.OrderCommandPort;
import com.orderpayment.application.order.port.out.OrderQueryPort;
import com.orderpayment.application.payment.dto.ConfirmPaymentCommand;
import com.orderpayment.application.payment.dto.ConfirmPaymentResult;
import com.orderpayment.application.payment.port.out.InventoryReservationCommandPort;
import com.orderpayment.application.payment.port.out.InventoryReservationQueryPort;
import com.orderpayment.application.payment.port.out.PaymentApprovalPort;
import com.orderpayment.application.payment.port.out.PaymentApprovalResult;
import com.orderpayment.application.payment.port.out.PaymentCommandPort;
import com.orderpayment.application.payment.port.out.PaymentQueryPort;
import com.orderpayment.application.payment.service.ConfirmPaymentIdempotencyHandler;
import com.orderpayment.application.payment.service.ConfirmPaymentIdempotencyResponse;
import com.orderpayment.application.payment.service.ConfirmPaymentIdempotencyResponseSerializer;
import com.orderpayment.application.payment.service.ConfirmPaymentRequestHashService;
import com.orderpayment.application.payment.service.ConfirmPaymentService;
import com.orderpayment.application.payment.service.ConfirmPaymentTransactionService;
import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.common.event.DomainEvent;
import com.orderpayment.domain.idempotency.IdempotencyRecord;
import com.orderpayment.domain.inventory.Inventory;
import com.orderpayment.domain.inventory.InventoryReservation;
import com.orderpayment.domain.inventory.InventoryReservationId;
import com.orderpayment.domain.inventory.InventoryReservationStatus;
import com.orderpayment.domain.order.Order;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.order.OrderItem;
import com.orderpayment.domain.order.OrderStatus;
import com.orderpayment.domain.payment.IdempotencyKey;
import com.orderpayment.domain.payment.Payment;
import com.orderpayment.domain.payment.PaymentId;
import com.orderpayment.domain.payment.PaymentStatus;
import com.orderpayment.domain.product.ProductId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConfirmPaymentServiceTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 5, 4, 10, 0);
    private final ProductId productId = ProductId.newId();
    private final OrderId orderId = OrderId.newId();
    private final PaymentId paymentId = new PaymentId(UUID.fromString("00000000-0000-0000-0000-000000000301"));
    private final IdempotencyKey idempotencyKey = new IdempotencyKey("payment-request-1");
    private final Map<OrderId, Order> orders = new LinkedHashMap<>();
    private final Map<PaymentId, Payment> payments = new LinkedHashMap<>();
    private final Map<String, IdempotencyRecord> idempotencyRecords = new LinkedHashMap<>();
    private final Map<ProductId, Inventory> inventories = new LinkedHashMap<>();
    private final List<InventoryReservation> reservations = new ArrayList<>();
    private final List<DomainEvent> publishedEvents = new ArrayList<>();
    private final AtomicInteger forUpdateReadCount = new AtomicInteger();

    @Mock
    private OrderQueryPort orderQueryPort;

    @Mock
    private OrderCommandPort orderCommandPort;

    @Mock
    private PaymentQueryPort paymentQueryPort;

    @Mock
    private PaymentCommandPort paymentCommandPort;

    @Mock
    private InventoryReservationQueryPort inventoryReservationQueryPort;

    @Mock
    private InventoryReservationCommandPort inventoryReservationCommandPort;

    @Mock
    private IdempotencyRecordQueryPort idempotencyRecordQueryPort;

    @Mock
    private IdempotencyRecordCommandPort idempotencyRecordCommandPort;

    @Mock
    private PaymentApprovalPort paymentApprovalPort;

    @Mock
    private DomainEventPublisherPort eventPublisher;

    private ConfirmPaymentRequestHashService requestHashService;
    private ConfirmPaymentIdempotencyResponseSerializer responseSerializer;
    private ConfirmPaymentService service;

    @BeforeEach
    void setUp() {
        requestHashService = new ConfirmPaymentRequestHashService();
        responseSerializer = new ConfirmPaymentIdempotencyResponseSerializer(new ObjectMapper());
        ConfirmPaymentIdempotencyHandler idempotencyHandler = new ConfirmPaymentIdempotencyHandler(
                idempotencyRecordQueryPort,
                idempotencyRecordCommandPort,
                () -> now,
                requestHashService,
                responseSerializer
        );
        ConfirmPaymentTransactionService transactionService = new ConfirmPaymentTransactionService(
                paymentQueryPort,
                paymentCommandPort,
                orderQueryPort,
                orderCommandPort,
                inventoryReservationQueryPort,
                inventoryReservationCommandPort,
                () -> now,
                eventPublisher,
                idempotencyHandler
        );
        service = new ConfirmPaymentService(paymentApprovalPort, transactionService, new SimpleMeterRegistry());
        stubStatefulPorts();
    }

    @Test
    @DisplayName("confirm 은 결제를 승인하고 재고 예약을 확정한다")
    void confirm_whenApprovalSucceeds_thenApprovePaymentAndConfirmReservation() {
        givenPreparedPayment();
        saveInventory(Inventory.restore(productId, 3, 2));
        when(paymentApprovalPort.approve(any(Payment.class)))
                .thenAnswer(invocation -> PaymentApprovalResult.approved("mock-pg-" + invocation.<Payment>getArgument(0).id().value()));

        ConfirmPaymentResult result = service.confirm(command());

        assertThat(result.paymentId()).isEqualTo(paymentId);
        assertThat(result.orderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(inventories.get(productId).heldQuantity()).isZero();
        assertThat(firstReservation().status()).isEqualTo(InventoryReservationStatus.CONFIRMED);
        assertThat(publishedEvents).hasSize(2);
    }

    @Test
    @DisplayName("confirm 은 이미 승인된 결제이면 기존 결과를 반환한다")
    void confirm_whenPaymentAlreadyApproved_thenReturnExistingResult() {
        givenPreparedPayment();
        saveInventory(Inventory.restore(productId, 3, 2));
        when(paymentApprovalPort.approve(any(Payment.class)))
                .thenAnswer(invocation -> PaymentApprovalResult.approved("mock-pg-" + invocation.<Payment>getArgument(0).id().value()));

        ConfirmPaymentResult firstResult = service.confirm(command());
        ConfirmPaymentResult secondResult = service.confirm(command());

        assertThat(secondResult).isEqualTo(firstResult);
        verify(paymentApprovalPort, times(1)).approve(any(Payment.class));
        verify(paymentCommandPort, times(2)).savePayment(any(Payment.class));
        assertThat(publishedEvents).hasSize(2);
    }

    @Test
    @DisplayName("confirm 은 중복 동시 요청에서도 PG 승인을 한 번만 호출한다")
    void confirm_whenDuplicateConcurrentRequests_thenApproveOnlyOnce() throws Exception {
        givenPreparedPayment();
        saveInventory(Inventory.restore(productId, 3, 2));
        CountDownLatch approvalStartedLatch = new CountDownLatch(1);
        CountDownLatch releaseApprovalLatch = new CountDownLatch(1);
        when(paymentApprovalPort.approve(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            approvalStartedLatch.countDown();
            await(releaseApprovalLatch);
            return PaymentApprovalResult.approved("mock-pg-" + payment.id().value());
        });

        int requestCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        for (int i = 0; i < requestCount; i++) {
            executorService.submit(() -> {
                readyLatch.countDown();
                await(startLatch);
                service.confirm(command());
            });
        }

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();
        assertThat(approvalStartedLatch.await(5, TimeUnit.SECONDS)).isTrue();
        awaitUntilPaymentProcessing();
        releaseApprovalLatch.countDown();
        executorService.shutdown();
        assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        verify(paymentApprovalPort, times(1)).approve(any(Payment.class));
        verify(paymentCommandPort, times(2)).savePayment(any(Payment.class));
        assertThat(payments.get(paymentId).status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(orders.get(orderId).status()).isEqualTo(OrderStatus.PAID);
        assertThat(firstReservation().status()).isEqualTo(InventoryReservationStatus.CONFIRMED);
        assertThat(publishedEvents).hasSize(2);
    }

    @Test
    @DisplayName("confirm 은 결제 승인 실패 시 결제를 실패 처리하고 재고 예약을 해제한다")
    void confirm_whenApprovalFails_thenFailPaymentAndReleaseReservation() {
        givenPreparedPayment();
        saveInventory(Inventory.restore(productId, 3, 2));
        when(paymentApprovalPort.approve(any(Payment.class)))
                .thenReturn(PaymentApprovalResult.failed("mock-pg-" + paymentId.value(), "mock approval failed"));

        ConfirmPaymentResult result = service.confirm(command());

        assertThat(result.orderStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(inventories.get(productId).availableQuantity()).isEqualTo(5);
        assertThat(inventories.get(productId).heldQuantity()).isZero();
        assertThat(firstReservation().status()).isEqualTo(InventoryReservationStatus.RELEASED);
        assertThat(publishedEvents).hasSize(2);
    }

    @Test
    @DisplayName("confirm 은 결제 준비 멱등키와 다른 승인 멱등키도 허용한다")
    void confirm_whenConfirmIdempotencyKeyDiffersFromPrepareKey_thenApprovePayment() {
        givenPreparedPayment();
        saveInventory(Inventory.restore(productId, 3, 2));
        when(paymentApprovalPort.approve(any(Payment.class)))
                .thenAnswer(invocation -> PaymentApprovalResult.approved("mock-pg-" + invocation.<Payment>getArgument(0).id().value()));

        ConfirmPaymentResult result = service.confirm(new ConfirmPaymentCommand(paymentId, new IdempotencyKey("another-key")));

        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.APPROVED);
        verify(paymentApprovalPort).approve(any(Payment.class));
    }

    @Test
    @DisplayName("confirm 은 결제가 이미 처리 중이면 예외를 던진다")
    void confirm_whenPaymentAlreadyProcessing_thenThrowException() {
        givenPreparedPayment();
        Payment payment = payments.get(paymentId);
        payment.startApproval(now.minusMinutes(1));
        savePayment(payment);

        assertThatThrownBy(() -> service.confirm(command()))
                .isInstanceOf(PaymentInProgressException.class);
        verify(paymentApprovalPort, times(0)).approve(any(Payment.class));
    }

    @Test
    @DisplayName("confirm 은 취소된 결제이면 예외를 던진다")
    void confirm_whenPaymentCancelled_thenThrowException() {
        givenPreparedPayment();
        Payment payment = payments.get(paymentId);
        payment.cancel();
        savePayment(payment);

        assertThatThrownBy(() -> service.confirm(command()))
                .isInstanceOf(DomainException.class);
        verify(paymentApprovalPort, times(0)).approve(any(Payment.class));
    }

    @Test
    @DisplayName("confirm 은 같은 멱등키와 결제 ID의 완료 응답을 재현한다")
    void confirm_whenCompletedIdempotencyRecordExists_thenReplayResponse() {
        givenPreparedPayment();
        ConfirmPaymentResult completedResult = new ConfirmPaymentResult(
                paymentId,
                orderId,
                Money.won(2000),
                OrderStatus.PAID,
                PaymentStatus.APPROVED
        );
        idempotencyRecords.put(command().idempotencyKey().value(), completedRecord(command(), completedResult));

        ConfirmPaymentResult result = service.confirm(command());

        assertThat(result).isEqualTo(completedResult);
        verify(paymentApprovalPort, times(0)).approve(any(Payment.class));
        assertThat(payments.get(paymentId).status()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    @DisplayName("confirm 은 같은 멱등키로 다른 결제를 요청하면 충돌 예외를 던진다")
    void confirm_whenSameIdempotencyKeyUsedForDifferentPayment_thenThrowConflict() {
        givenPreparedPayment();
        PaymentId anotherPaymentId = PaymentId.newId();
        ConfirmPaymentCommand firstCommand = new ConfirmPaymentCommand(anotherPaymentId, idempotencyKey);
        ConfirmPaymentResult completedResult = new ConfirmPaymentResult(
                anotherPaymentId,
                orderId,
                Money.won(2000),
                OrderStatus.PAID,
                PaymentStatus.APPROVED
        );
        idempotencyRecords.put(idempotencyKey.value(), completedRecord(firstCommand, completedResult));

        assertThatThrownBy(() -> service.confirm(command()))
                .isInstanceOf(IdempotencyKeyConflictException.class);
        verify(paymentApprovalPort, times(0)).approve(any(Payment.class));
    }

    private void stubStatefulPorts() {
        when(idempotencyRecordQueryPort.findByKey(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(idempotencyRecords.get(invocation.getArgument(0))));
        doAnswer(invocation -> {
            IdempotencyRecord record = invocation.getArgument(0);
            idempotencyRecords.put(record.key(), record);
            return null;
        }).when(idempotencyRecordCommandPort).save(any(IdempotencyRecord.class));
        when(paymentQueryPort.getPaymentForUpdate(any(PaymentId.class))).thenAnswer(invocation -> {
            PaymentId id = invocation.getArgument(0);
            if (forUpdateReadCount.incrementAndGet() > 1) {
                awaitUntilPaymentStatusChangesFromReady(id);
            }
            return payment(id);
        });
        when(paymentQueryPort.getPayment(any(PaymentId.class))).thenAnswer(invocation -> payment(invocation.getArgument(0)));
        when(orderQueryPort.getOrder(any(OrderId.class))).thenAnswer(invocation -> order(invocation.getArgument(0)));
        doAnswer(invocation -> {
            savePayment(invocation.getArgument(0));
            return null;
        }).when(paymentCommandPort).savePayment(any(Payment.class));
        doAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            orders.put(order.id(), order);
            return null;
        }).when(orderCommandPort).saveOrder(any(Order.class));
        when(inventoryReservationQueryPort.findHeldReservationsByOrderId(any(OrderId.class))).thenAnswer(invocation -> {
            OrderId orderId = invocation.getArgument(0);
            return reservations.stream()
                    .filter(reservation -> reservation.orderId().equals(orderId))
                    .filter(reservation -> reservation.status() == InventoryReservationStatus.HELD)
                    .toList();
        });
        doAnswer(invocation -> {
            invocation.<List<InventoryReservation>>getArgument(0)
                    .forEach(reservation -> inventories.get(reservation.productId()).confirm(reservation.quantity()));
            return null;
        }).when(inventoryReservationCommandPort).confirmAll(any());
        doAnswer(invocation -> {
            invocation.<List<InventoryReservation>>getArgument(0)
                    .forEach(reservation -> inventories.get(reservation.productId()).release(reservation.quantity()));
            return null;
        }).when(inventoryReservationCommandPort).releaseAll(any());
        doAnswer(invocation -> {
            publishedEvents.addAll(invocation.getArgument(0));
            return null;
        }).when(eventPublisher).publishAll(any());
    }

    private void givenPreparedPayment() {
        Order order = Order.create(orderId, List.of(
                OrderItem.of(productId, "keyboard", Money.won(1000), 2)
        ));
        order.requestPayment();
        orders.put(orderId, order);
        savePayment(Payment.ready(paymentId, orderId, Money.won(2000), idempotencyKey));
        reservations.add(InventoryReservation.hold(
                InventoryReservationId.newId(),
                orderId,
                productId,
                2,
                now.plusMinutes(10)
        ));
    }

    private ConfirmPaymentCommand command() {
        return new ConfirmPaymentCommand(paymentId, idempotencyKey);
    }

    private Payment payment(PaymentId paymentId) {
        return Optional.ofNullable(payments.get(paymentId))
                .orElseThrow(() -> new DomainException("payment not found"));
    }

    private Order order(OrderId orderId) {
        return Optional.ofNullable(orders.get(orderId))
                .orElseThrow(() -> new DomainException("order not found"));
    }

    private void savePayment(Payment payment) {
        payments.put(payment.id(), payment);
    }

    private void saveInventory(Inventory inventory) {
        inventories.put(inventory.productId(), inventory);
    }

    private InventoryReservation firstReservation() {
        return reservations.get(0);
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

    private void awaitUntilPaymentProcessing() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (payments.get(paymentId).status() == PaymentStatus.PROCESSING) {
                return;
            }
            Thread.yield();
        }
        throw new AssertionError("payment did not enter PROCESSING status");
    }

    private void awaitUntilPaymentStatusChangesFromReady(PaymentId paymentId) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (payments.get(paymentId).status() != PaymentStatus.READY) {
                return;
            }
            Thread.yield();
        }
        throw new AssertionError("payment row lock was not released");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
